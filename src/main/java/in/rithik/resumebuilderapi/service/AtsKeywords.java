package in.rithik.resumebuilderapi.service;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * AtsKeywords — static keyword lists grouped by job category.
 *
 * HOW IT WORKS:
 * When a user wants their resume refined, we look at their job title/designation
 * and find the best matching category here. Then we check which of that category's
 * important keywords are missing from their resume content.
 *
 * If "React" is important for Frontend Engineers but isn't in the resume at all,
 * we flag it as a suggestion (not an error — maybe they just didn't list it).
 *
 * These are intentionally broad — they catch the most impactful gaps only.
 * Nitpicky keyword matching would produce noise, not signal.
 */
@Component
public class AtsKeywords {

    // Each category maps a label (for matching against job titles) to its keyword list
    public static final Map<String, List<String>> CATEGORY_KEYWORDS = new LinkedHashMap<>();

    static {
        CATEGORY_KEYWORDS.put("software engineer", Arrays.asList(
            "Java", "Python", "JavaScript", "TypeScript", "React", "Spring Boot",
            "REST API", "Microservices", "Docker", "Kubernetes", "CI/CD",
            "Git", "SQL", "MongoDB", "AWS", "unit testing", "Agile", "Scrum",
            "design patterns", "code review", "system design"
        ));

        CATEGORY_KEYWORDS.put("frontend developer", Arrays.asList(
            "React", "Vue", "Angular", "JavaScript", "TypeScript", "CSS", "HTML",
            "responsive design", "Webpack", "Vite", "performance optimization",
            "accessibility", "cross-browser", "Git", "REST API", "Figma"
        ));

        CATEGORY_KEYWORDS.put("backend developer", Arrays.asList(
            "Java", "Node.js", "Python", "REST API", "GraphQL", "SQL", "PostgreSQL",
            "MongoDB", "Redis", "Docker", "Kubernetes", "AWS", "authentication",
            "authorization", "JWT", "microservices", "Spring Boot", "Express.js"
        ));

        CATEGORY_KEYWORDS.put("data analyst", Arrays.asList(
            "SQL", "Python", "Excel", "Tableau", "Power BI", "data visualization",
            "statistical analysis", "ETL", "pandas", "NumPy", "data cleaning",
            "A/B testing", "dashboards", "KPIs", "business intelligence"
        ));

        CATEGORY_KEYWORDS.put("data scientist", Arrays.asList(
            "Python", "R", "machine learning", "deep learning", "TensorFlow",
            "PyTorch", "scikit-learn", "SQL", "statistics", "data preprocessing",
            "feature engineering", "model deployment", "Jupyter", "NLP", "pandas"
        ));

        CATEGORY_KEYWORDS.put("product manager", Arrays.asList(
            "product roadmap", "user stories", "Agile", "Scrum", "stakeholder",
            "KPIs", "OKRs", "market research", "user research", "A/B testing",
            "Jira", "Confluence", "prioritization", "go-to-market", "PRD",
            "cross-functional", "data-driven"
        ));

        CATEGORY_KEYWORDS.put("designer", Arrays.asList(
            "Figma", "Sketch", "Adobe XD", "Photoshop", "Illustrator", "UI", "UX",
            "wireframes", "prototypes", "usability testing", "design systems",
            "user research", "accessibility", "responsive design", "typography",
            "color theory", "Zeplin"
        ));

        CATEGORY_KEYWORDS.put("devops engineer", Arrays.asList(
            "Docker", "Kubernetes", "CI/CD", "Jenkins", "GitHub Actions", "AWS",
            "Azure", "GCP", "Terraform", "Ansible", "monitoring", "Prometheus",
            "Grafana", "Linux", "bash scripting", "infrastructure as code",
            "reliability", "SRE", "load balancing"
        ));

        CATEGORY_KEYWORDS.put("marketing", Arrays.asList(
            "SEO", "SEM", "Google Analytics", "content marketing", "social media",
            "email marketing", "campaign", "conversion rate", "ROI", "CRM",
            "Salesforce", "HubSpot", "A/B testing", "brand strategy", "lead generation",
            "digital marketing", "copywriting"
        ));

        CATEGORY_KEYWORDS.put("finance", Arrays.asList(
            "financial modeling", "Excel", "PowerPoint", "valuation", "DCF",
            "financial analysis", "budgeting", "forecasting", "P&L", "balance sheet",
            "ROI", "KPIs", "Bloomberg", "CFA", "compliance", "audit", "risk management"
        ));

        CATEGORY_KEYWORDS.put("project manager", Arrays.asList(
            "project planning", "stakeholder management", "risk management",
            "Agile", "Scrum", "waterfall", "MS Project", "Jira", "PMP",
            "budget management", "resource allocation", "milestone", "deliverables",
            "cross-functional", "status reporting"
        ));

        CATEGORY_KEYWORDS.put("healthcare", Arrays.asList(
            "patient care", "clinical", "EMR", "EHR", "HIPAA", "diagnosis",
            "treatment", "medication", "healthcare", "medical", "nursing",
            "patient assessment", "documentation", "compliance", "telehealth"
        ));
    }

    /**
     * Finds the best-matching category for a given job designation.
     * Uses simple case-insensitive substring matching — no ML needed.
     *
     * Example: "Senior Software Engineer" → matches "software engineer"
     *          "UI/UX Designer" → matches "designer"
     *
     * Returns the category key or null if nothing matches (we skip keyword check).
     */
    public static String detectCategory(String designation) {
        if (designation == null || designation.isBlank()) return null;
        String lower = designation.toLowerCase();
        for (String category : CATEGORY_KEYWORDS.keySet()) {
            // Check if any word from the category appears in the designation
            String[] parts = category.split(" ");
            boolean allMatch = Arrays.stream(parts).allMatch(lower::contains);
            if (allMatch) return category;
        }
        // Broader single-word fallback match
        for (String category : CATEGORY_KEYWORDS.keySet()) {
            String[] parts = category.split(" ");
            boolean anyMatch = Arrays.stream(parts).anyMatch(lower::contains);
            if (anyMatch) return category;
        }
        return null;
    }
}
