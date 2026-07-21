package com.careeros.backend.achievement.extractor;



import org.springframework.stereotype.Component;

@Component
public class TechnologyRules implements TechnologyRule {

    @Override
    public boolean matches(String file) {

        String f = file.toLowerCase();

        return f.endsWith(".java")
                || f.endsWith(".py")
                || f.endsWith(".cpp")
                || f.endsWith(".c")
                || f.endsWith(".js")
                || f.endsWith(".ts")
                || f.endsWith(".html")
                || f.endsWith(".css")
                || f.endsWith(".sql")
                || f.endsWith(".rs")
                || f.endsWith(".go")
                || f.endsWith(".md")
                || f.endsWith("dockerfile")
                || f.endsWith(".yml")
                || f.endsWith(".yaml")
                || f.endsWith("pom.xml")
                || f.endsWith("build.gradle")
                || f.endsWith("package.json");
    }

    @Override
    public String technology() {
        return "";
    }

    public String detect(String file) {

        String f = file.toLowerCase();

        if (f.endsWith(".java"))
            return "Java";

        if (f.endsWith(".py"))
            return "Python";

        if (f.endsWith(".cpp"))
            return "C++";

        if (f.endsWith(".c"))
            return "C";

        if (f.endsWith(".js"))
            return "JavaScript";

        if (f.endsWith(".ts"))
            return "TypeScript";

        if (f.endsWith(".html"))
            return "HTML";

        if (f.endsWith(".css"))
            return "CSS";

        if (f.endsWith(".sql"))
            return "SQL";

        if (f.endsWith(".rs"))
            return "Rust";

        if (f.endsWith(".go"))
            return "Go";

        if (f.endsWith("dockerfile"))
            return "Docker";

        if (f.endsWith(".yml") || f.endsWith(".yaml"))
            return "YAML";

        if (f.endsWith("pom.xml"))
            return "Maven";

        if (f.endsWith("build.gradle"))
            return "Gradle";

        if (f.endsWith("package.json"))
            return "Node.js";

        return null;
    }
}
