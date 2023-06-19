import groovy.xml.XmlParser
import org.gradle.api.JavaVersion.VERSION_17
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.tasks.PatchPluginXmlTask
import org.jetbrains.intellij.tasks.PrepareSandboxTask
import org.jetbrains.intellij.tasks.PublishPluginTask
import org.jetbrains.intellij.tasks.RunIdeTask

fun properties(key: String) = providers.gradleProperty(key)
fun environment(key: String) = providers.environmentVariable(key)

// The same as `--stacktrace` param
gradle.startParameter.showStacktrace = ShowStacktrace.ALWAYS

val isCI = environment("CI").isPresent
val isTeamcity = environment("TEAMCITY_VERSION").isPresent

val platformVersion = properties("platformVersion").get().toInt()
val baseIDE = properties("baseIDE").get()
val ideaVersion = properties("ideaVersion").get()
val clionVersion = properties("clionVersion").get()
val baseVersion = when (baseIDE) {
    "idea" -> ideaVersion
    "clion" -> clionVersion
    else -> error("Unexpected IDE name: `$baseIDE`")
}

//val psiViewerPlugin = "PsiViewer:${properties("psiViewerPluginVersion")}"
val javaPlugin = "com.intellij.java"
val clionPlugins = listOf("com.intellij.cidr.base", "com.intellij.clion")
val ideaPlugins = listOf(javaPlugin, "com.intellij.java.ide")

val basePluginArchiveName = "fortran-plugin"

group = properties("pluginGroup").get()
version = properties("pluginVersion").get()


plugins {
    id("java") // Java support
    alias(libs.plugins.kotlin) // Kotlin support
    alias(libs.plugins.gradleIntelliJPlugin) // Gradle IntelliJ Plugin
    alias(libs.plugins.changelog) // Gradle Changelog Plugin
    alias(libs.plugins.grammarkit) // Grammarkit plugin
}

changelog {
    groups.empty()
    repositoryUrl.set(properties("pluginRepositoryUrl").get())
}


allprojects {
    apply {
        plugin("idea")
        plugin("kotlin")
        plugin("org.jetbrains.grammarkit")
        plugin("org.jetbrains.intellij")
    }

    repositories {
        mavenCentral()
        maven("https://cache-redirector.jetbrains.com/repo.maven.apache.org/maven2")
        maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
    }

    idea {
        module {
            generatedSourceDirs.add(file("src/gen"))
        }
    }

    intellij {
        version.set(baseVersion)
        downloadSources.set(!isCI)
        updateSinceUntilBuild.set(true)
        instrumentCode.set(false)
        ideaDependencyCachePath.set(dependencyCachePath)
        sandboxDir.set("$buildDir/$baseIDE-sandbox-$platformVersion")
    }

    configure<JavaPluginExtension> {
        sourceCompatibility = VERSION_17
        targetCompatibility = VERSION_17
    }

    tasks {
        compileKotlin {
            kotlinOptions {
                jvmTarget = VERSION_17.toString()
                languageVersion = "1.8"
                // see https://plugins.jetbrains.com/docs/intellij/using-kotlin.html#kotlin-standard-library
                apiVersion = "1.7"
                freeCompilerArgs = listOf("-Xjvm-default=all")
            }
        }
        patchPluginXml {
            version.set(properties("pluginVersion").get())
            sinceBuild.set(properties("pluginSinceBuild").get())
            untilBuild.set(properties("pluginUntilBuild").get())
        }

        // All these tasks don't make sense for non-root subprojects
        // Root project (i.e. `:plugin`) enables them itself if needed
        runIde {
            enabled = false
        }
        prepareSandbox {
            enabled = false
        }
        buildSearchableOptions {
            enabled = false
        }

//        test {
//            testLogging {
//                showStandardStreams = properties("showStandardStreams").get().toBoolean()
//                afterSuite(
//                    KotlinClosure2<TestDescriptor, TestResult, Unit>({ desc, result ->
//                        if (desc.parent == null) { // will match the outermost suite
//                            val output = "Results: ${result.resultType} (${result.testCount} tests, ${result.successfulTestCount} passed, ${result.failedTestCount} failed, ${result.skippedTestCount} skipped)"
//                            println(output)
//                        }
//                    })
//                )
//            }
//        }
    }

    sourceSets {
        main {
            java.srcDirs("src/gen")
            resources.srcDirs("src/$platformVersion/main/resources")
        }
        test {
            resources.srcDirs("src/$platformVersion/test/resources")
        }
    }
    kotlin {
        sourceSets {
            main {
                kotlin.srcDirs("src/$platformVersion/main/kotlin")
            }
            test {
                kotlin.srcDirs("src/$platformVersion/test/kotlin")
            }
        }
    }

//    val testOutput = configurations.create("testOutput")

    dependencies {
//        compileOnly(kotlin("stdlib-jdk8"))
//        testOutput(sourceSets.getByName("test").output.classesDirs)
    }

    afterEvaluate {
        tasks.withType<AbstractTestTask> {
            testLogging {
                if (properties("showTestStatus").isPresent && properties("showTestStatus").get().toBoolean()) {
                    events = setOf(TestLogEvent.STARTED, TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
                }
                exceptionFormat = TestExceptionFormat.FULL
            }
        }

        tasks.withType<Test>().configureEach {
            jvmArgs = listOf("-Xmx2g", "-XX:-OmitStackTraceInFastThrow")
            // We need to prevent the platform-specific shared JNA library to loading from the system library paths,
            // because otherwise it can lead to compatibility issues.
            // Also note that IDEA does the same thing at startup, and not only for tests.
            systemProperty("jna.nosys", "true")
            if (isTeamcity) {
                // Make teamcity builds green if only muted tests fail
                // https://youtrack.jetbrains.com/issue/TW-16784
                ignoreFailures = true
            }
            if (properties("excludeTests").isPresent) {
                exclude(properties("excludeTests").get())
            }
        }
    }
}

// Boilerplate
val Project.dependencyCachePath
    get(): String {
        val cachePath = file("${rootProject.projectDir}/deps")
        // If cache path doesn't exist, we need to create it manually
        // because otherwise gradle-intellij-plugin will ignore it
        if (!cachePath.exists()) {
            cachePath.mkdirs()
        }
        return cachePath.absolutePath
    }

// Special module with run, build and publish tasks
project(":plugin") {
    version = properties("pluginVersion").get()
    intellij {
        pluginName.set("fortran-plugin")
        val pluginList = mutableListOf<String>(
//            psiViewerPlugin,
        )
        if (baseIDE == "idea") {
            pluginList += listOf(
//                copyrightPlugin,
                javaPlugin,
            )
        }
        plugins.set(pluginList)
    }

    dependencies {
        implementation(project(":"))
        implementation(project(":idea"))
//        implementation(project(":clion"))

//        implementation(project(":debugger"))
//        implementation(project(":profiler"))
//        implementation(project(":copyright"))
//        implementation(project(":coverage"))
//        implementation(project(":intelliLang"))
//        implementation(project(":duplicates"))
//        implementation(project(":grazie"))
    }

    // TODO: not sure how to handle this
    // Collects all jars produced by compilation of project modules and merges them into singe one.
    // We need to put all plugin manifest files into single jar to make new plugin model work
    val mergePluginJarTask = task<Jar>("mergePluginJars") {
        duplicatesStrategy = DuplicatesStrategy.FAIL
        archiveBaseName.set(basePluginArchiveName)

        exclude("META-INF/MANIFEST.MF")
        exclude("**/classpath.index")

        val pluginLibDir by lazy {
            val sandboxTask = tasks.prepareSandbox.get()
            sandboxTask.destinationDir.resolve("${sandboxTask.pluginName.get()}/lib")
        }

        val pluginJars by lazy {
            pluginLibDir.listFiles().orEmpty().filter { it.isPluginJar() }
        }

        destinationDirectory.set(project.layout.dir(provider { pluginLibDir }))

        doFirst {
            for (file in pluginJars) {
                from(zipTree(file))
            }
        }

        doLast {
            delete(pluginJars)
        }
    }

    // Add plugin sources to the plugin ZIP.
    // gradle-intellij-plugin will use it as a plugin sources if the plugin is used as a dependency
    val createSourceJar = task<Jar>("createSourceJar") {
        dependsOn(":generateLexer")
        dependsOn(":generateParser")
        dependsOn(":debugger:generateGrammarSource")

        for (prj in rootProject.allprojects) {
            from(prj.kotlin.sourceSets.main.get().kotlin) {
                include("**/*.java")
                include("**/*.kt")
            }
        }

        destinationDirectory.set(layout.buildDirectory.dir("libs"))
        archiveBaseName.set(basePluginArchiveName)
        archiveClassifier.set("src")
    }

    tasks {
        buildPlugin {
            dependsOn(createSourceJar)
            from(createSourceJar) { into("lib/src") }
            // Set proper name for final plugin zip.
            // Otherwise, base name is the same as gradle module name
            archiveBaseName.set(basePluginArchiveName)
        }

        runIde {
//            dependsOn(mergePluginJarTask)
            enabled = true
        }
        prepareSandbox {
//            finalizedBy(mergePluginJarTask)
            enabled = true
        }

        buildSearchableOptions {
            // Force `mergePluginJarTask` be executed before `buildSearchableOptions`
            // Otherwise, `buildSearchableOptions` task can't load the plugin and searchable options are not built.
            // Should be dropped when jar merging is implemented in `gradle-intellij-plugin` itself
//            dependsOn(mergePluginJarTask)
            enabled = properties("enableBuildSearchableOptions").get().toBoolean()
        }

        withType<PrepareSandboxTask> {
            // Copy native binaries
            from("${rootDir}/bin") {
                into("${pluginName.get()}/bin")
                include("**")
            }
        }

        withType<RunIdeTask> {
            // Default args for IDEA installation
            jvmArgs("-Xmx768m", "-XX:+UseG1GC", "-XX:SoftRefLRUPolicyMSPerMB=50")
//            // Disable plugin auto reloading. See `com.intellij.ide.plugins.DynamicPluginVfsListener`
//            jvmArgs("-Didea.auto.reload.plugins=false")
            // Don't show "Tip of the Day" at startup
            jvmArgs("-Dide.show.tips.on.startup.default.value=false")
            // uncomment if `unexpected exception ProcessCanceledException` prevents you from debugging a running IDE
            // jvmArgs("-Didea.ProcessCanceledException=disabled")

            // Uncomment to enable FUS testing mode
            // jvmArgs("-Dfus.internal.test.mode=true")

            // Uncomment to enable localization testing mode
            // jvmArgs("-Didea.l10n=true")
        }

        withType<PatchPluginXmlTask> {

//            val changelog = project.changelog // local variable for configuration cache compatibility
            pluginDescription.set(providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
                // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
                val start = "<!-- Plugin description -->"
                val end = "<!-- Plugin description end -->"

                with (it.lines()) {
                    if (!containsAll(listOf(start, end))) {
                        throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                    }
                    subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
                }
            })

            // Get the latest available change notes from the changelog file
//            changeNotes.set(properties("pluginVersion").map { pluginVersion ->
//                with(changelog) {
//                    renderItem(
//                        (getOrNull(pluginVersion) ?: getUnreleased())
//                            .withHeader(false)
//                            .withEmptySections(false),
//                        Changelog.OutputType.HTML,
//                    )
//                }
//            })
        }

        signPlugin {
            certificateChain.set(environment("CERTIFICATE_CHAIN"))
            privateKey.set(environment("PRIVATE_KEY"))
            password.set(environment("PRIVATE_KEY_PASSWORD"))
        }

        withType<PublishPluginTask> {
            dependsOn("patchChangelog")
            token.set(environment("PUBLISH_TOKEN"))
            // The pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
            // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
            // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
            channels.set(properties("pluginVersion").map { listOf(it.split('-').getOrElse(1) { "default" }.split('.').first()) })
        }
    }

    // Generates event scheme for Rust plugin FUS events to `plugin/build/eventScheme.json`
    task<RunIdeTask>("buildEventsScheme") {
        dependsOn(tasks.prepareSandbox)
        args("buildEventsScheme", "--outputFile=${buildDir.resolve("eventScheme.json").absolutePath}", "--pluginId=org.jetbrains.fortran")
        // BACKCOMPAT: 2022.3. Update value to 231 and this comment
        // `IDEA_BUILD_NUMBER` variable is used by `buildEventsScheme` task to write `buildNumber` to output json.
        // It will be used by TeamCity automation to set minimal IDE version for new events
        environment("IDEA_BUILD_NUMBER", "223")
    }
}

project(":") {
//    dependencies {
//        implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-toml:2.14.2") {
//            exclude(module = "jackson-core")
//            exclude(module = "jackson-databind")
//            exclude(module = "jackson-annotations")
//        }
//        api("io.github.z4kn4fein:semver:1.4.2") {
//            excludeKotlinDeps()
//        }
//        testImplementation("com.squareup.okhttp3:mockwebserver:4.11.0")
//    }

    tasks {
        wrapper {
            gradleVersion = properties("gradleVersion").get()
        }
        generateLexer {
            sourceFile.set(file("src/main/grammars/FortranLexer.flex"))
            targetDir.set("src/gen/org/jetbrains/fortran/lang/lexer")
            targetClass.set("_FortranLexer")
            purgeOldFiles.set(true)
        }
        generateParser {
            sourceFile.set(file("src/main/grammars/FortranParser.bnf"))
            targetRoot.set("src/gen")
            pathToParser.set("org/jetbrains/fortran/parser/FortranParser.java")
            pathToPsiRoot.set("org/jetbrains/fortran/lang/psi")
            purgeOldFiles.set(true)
        }
        withType<KotlinCompile> {
            dependsOn(generateLexer, generateParser)
        }

//        // In tests `resources` directory is used instead of `sandbox`
//        processTestResources {
//            dependsOn(named("compileNativeCode"))
//            from("${rootDir}/bin") {
//                into("bin")
//                include("**")
//            }
//        }
    }

    task("resolveDependencies") {
        doLast {
            rootProject.allprojects
                .map { it.configurations }
                .flatMap { it.filter { c -> c.isCanBeResolved } }
                .forEach { it.resolve() }
        }
    }


//    task("resolveDependencies") {
//        doLast {
//            rootProject.allprojects
//                .map { it.configurations }
//                .flatMap { listOf(it.getByName("compile"), it.getByName("testCompile")) }
//                .forEach { it.resolve() }
//        }
//    }
}

project(":idea") {
    intellij {
        version.set(ideaVersion)
        plugins.set(ideaPlugins)
    }
    dependencies {
        implementation(project(":"))
//        testImplementation(project(":", "testOutput"))
    }
}

project(":clion") {
    intellij {
        version.set(clionVersion)
        plugins.set(clionPlugins)
        type.set("CL")
    }
    dependencies {
        implementation(project(":"))
//        implementation(project(":debugger"))
//        testImplementation(project(":", "testOutput"))
    }
}

// Integrations

// TODO: Make a separate debugger plugin?
//project(":debugger") {
//    apply {
//        plugin("antlr")
//    }
//    intellij {
//        if (baseIDE == "idea") {
//            plugins.set(listOf(nativeDebugPlugin))
//        } else {
//            version.set(clionVersion)
//            plugins.set(clionPlugins)
//        }
//    }
//
//    // Kotlin Gradle support doesn't generate proper extensions if the plugin is not declared in `plugin` block.
//    // But if we do it, `antlr` plugin will be applied to root project as well that we want to avoid.
//    // So, let's define all necessary things manually
//    val antlr by configurations
//    val generateGrammarSource: AntlrTask by tasks
//    val generateTestGrammarSource: AntlrTask by tasks
//
//    dependencies {
//        implementation(project(":"))
//        antlr("org.antlr:antlr4:4.12.0")
//        implementation("org.antlr:antlr4-runtime:4.12.0")
//        testImplementation(project(":", "testOutput"))
//    }
//    tasks {
//        compileKotlin {
//            dependsOn(generateGrammarSource)
//        }
//        compileTestKotlin {
//            dependsOn(generateTestGrammarSource)
//        }
//
//        generateGrammarSource {
//            arguments.add("-no-listener")
//            arguments.add("-visitor")
//            outputDirectory = file("src/gen/org/rust/debugger/lang")
//        }
//    }
//    // Exclude antlr4 from transitive dependencies of `:debugger:api` configuration (https://github.com/gradle/gradle/issues/820)
//    configurations.api {
//        setExtendsFrom(extendsFrom.filter { it.name != "antlr" })
//    }
//}

//project(":intelliLang") {
//    intellij {
//        plugins.set(listOf(intelliLangPlugin))
//    }
//    dependencies {
//        implementation(project(":"))
//        testImplementation(project(":", "testOutput"))
//    }
//}

// TODO: Copyright plugin (to generate copyright message in fortran style)
//project(":copyright") {
//    intellij {
//        version.set(ideaVersion)
//        plugins.set(listOf(copyrightPlugin))
//    }
//    dependencies {
//        implementation(project(":"))
//        testImplementation(project(":", "testOutput"))
//    }
//}

// More boilerplate

fun File.isPluginJar(): Boolean {
    if (!isFile) return false
    if (extension != "jar") return false
    return zipTree(this).files.any { it.isManifestFile() }
}
fun File.isManifestFile(): Boolean {
    if (extension != "xml") return false
    val rootNode = try {
        val parser = XmlParser()
        parser.parse(this)
    } catch (e: Exception) {
        logger.error("Failed to parse $path", e)
        return false
    }
    return rootNode.name() == "idea-plugin"
}
