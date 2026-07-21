package com.careeros.backend.achievement.linkedin;

import com.careeros.backend.achievement.weekly.WeeklySummary;
import org.springframework.stereotype.Component;

@Component
public class LinkedInPromptBuilder {

    public String build(WeeklySummary summary) {

        return """
                You are a software engineer writing a LinkedIn post about your week's work.
                
                The post should sound like it was written by a real developer sharing progress with their network.
                
                WRITING STYLE
                
                - Write naturally.
                - Write in first person.
                - Be authentic.
                - Mention what you actually built.
                - Keep the tone confident but humble.
                - Avoid sounding like an AI assistant.
                - Avoid sounding like corporate marketing.
                
                DO NOT USE
                
                - Thrilled to share...
                - Excited to announce...
                - Honored to...
                - Delighted to...
                - Leveraged
                - Utilized
                - Cutting-edge
                - Robust
                - Comprehensive
                - Amazing
                - Incredible journey
                
                Instead, write like an engineer reflecting on their work.
                
                GOOD EXAMPLES
                
                ✓ Spent some time this week expanding the Hacktoberfest repository. I added an F1 Race Predictor, implemented a few algorithm solutions, improved the contribution guide, and started testing the shortest path implementation. Looking forward to building more over the next few weeks.
                
                ✓ Wrapped up a productive week working on the Hacktoberfest repository. Added an F1 Race Predictor, implemented several algorithms, and improved the contributor documentation. Small progress every week really adds up.
                
                RULES
                
                - Use ONLY the provided evidence.
                - Never invent achievements.
                - Never invent technologies.
                - Never invent business impact.
                - Maximum 180 words.
                - No hashtags.
                - No emojis.
                - End naturally.
                - Return ONLY JSON.
                
                {
                  "headline":"",
                  "post":"",
                  "confidence":0.95
                }
                
                ====================
                
                Title:
                """
                + summary.getTitle()

                + """
                
                Summary:
                """
                + summary.getSummary()

                + """
                
                Highlights:
                """
                + summary.getHighlights()

                + """
                
                Technologies:
                """
                + summary.getTechnologies()

                + """
                
                Return JSON only.
                """;

    }
}