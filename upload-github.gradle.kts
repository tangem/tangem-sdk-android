apply(plugin = "maven-publish")

val artifactConfig = mapOf(
    "group" to "com.tangem.tangem-sdk-kotlin",
    "version" to if (project.hasProperty("artifactVersion")) project.property("artifactVersion") as String else "0.0.1",
)

val artifactId: String by project

afterEvaluate {
    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("maven") {
                groupId = artifactConfig["group"]
                this.artifactId = artifactId
                version = artifactConfig["version"]

                if (artifactId == "android") {
                    from(components["release"])

                    pom.withXml {
                        val pomNode = asNode()
                        val depsList = pomNode.get("dependencies") as? groovy.util.NodeList ?: return@withXml
                        for (depsNode in depsList) {
                            val deps = (depsNode as groovy.util.Node).children().toMutableList()
                            // Remove internal module dependencies
                            deps.filterIsInstance<groovy.util.Node>()
                                .filter { dep ->
                                    (dep.get("groupId") as groovy.util.NodeList).text() == artifactConfig["group"]
                                }
                                .forEach { dep -> dep.parent().remove(dep) }
                            // Change runtime scope to compile
                            (depsNode.children() as List<*>)
                                .filterIsInstance<groovy.util.Node>()
                                .filter { dep ->
                                    (dep.get("scope") as groovy.util.NodeList).text() == "runtime"
                                }
                                .forEach { dep ->
                                    val scopeNode = (
                                        dep.get(
                                            "scope",
                                        ) as groovy.util.NodeList
                                        ).first() as groovy.util.Node
                                    scopeNode.setValue("compile")
                                }
                        }
                    }
                } else if (artifactId == "core") {
                    from(components["java"])
                }
            }
        }
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/tangem/tangem-sdk-android")
                credentials {
                    username = findProperty("githubUser") as? String
                    password = findProperty("githubPass") as? String
                }
            }
        }
    }
}