package in.rithik.resumebuilderapi.service;

import in.rithik.resumebuilderapi.document.Resume;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class AtsKeywords {

    public static Map<String, List<String>> getCategoryKeywords() {
        return AtsRulesLoader.getConfig().getCategories();
    }

    public static String detectCategory(String designation, Resume resume) {
        if (designation == null || designation.isBlank()) {
            return "General Resume";
        }
        
        String lowerDes = designation.toLowerCase(Locale.ROOT);
        String summary = "";
        List<String> skills = new ArrayList<>();
        
        if (resume != null) {
            if (resume.getProfileInfo() != null && resume.getProfileInfo().getSummary() != null) {
                summary = resume.getProfileInfo().getSummary().toLowerCase(Locale.ROOT);
            }
            if (resume.getSkills() != null) {
                for (Resume.Skill s : resume.getSkills()) {
                    if (s.getName() != null) {
                        skills.add(s.getName().toLowerCase(Locale.ROOT));
                    }
                }
            }
        }

        // Define primary keywords for each role
        Map<String, List<String>> roleMap = new LinkedHashMap<>();
        roleMap.put("Software Engineer", Arrays.asList("software engineer", "software developer", "swe", "programmer"));
        roleMap.put("Java Developer", Arrays.asList("java developer", "java engineer", "java backend"));
        roleMap.put("Backend Developer", Arrays.asList("backend developer", "backend engineer", "api developer"));
        roleMap.put("Frontend Developer", Arrays.asList("frontend developer", "frontend engineer", "react developer", "ui developer"));
        roleMap.put("Full Stack Developer", Arrays.asList("full stack developer", "full stack engineer", "fullstack"));
        roleMap.put("Android Developer", Arrays.asList("android developer", "android engineer", "mobile developer", "ios developer", "flutter"));
        roleMap.put("Data Analyst", Arrays.asList("data analyst", "bi analyst", "analytics", "business intelligence"));
        roleMap.put("Data Scientist", Arrays.asList("data scientist", "data science"));
        roleMap.put("Machine Learning Engineer", Arrays.asList("machine learning engineer", "ml engineer", "nlp engineer", "deep learning"));
        roleMap.put("AI Engineer", Arrays.asList("ai engineer", "artificial intelligence engineer", "ai developer"));
        roleMap.put("Cloud Engineer", Arrays.asList("cloud engineer", "cloud architect", "aws engineer", "azure engineer"));
        roleMap.put("DevOps Engineer", Arrays.asList("devops engineer", "site reliability engineer", "sre", "infrastructure engineer"));
        roleMap.put("QA Engineer", Arrays.asList("qa engineer", "quality assurance", "software tester"));
        roleMap.put("Automation Tester", Arrays.asList("automation tester", "test automation", "qa automation"));
        roleMap.put("Cyber Security", Arrays.asList("cyber security", "information security", "penetration tester", "security analyst"));
        roleMap.put("Network Engineer", Arrays.asList("network engineer", "network administrator"));
        roleMap.put("UI Designer", Arrays.asList("ui designer", "user interface designer"));
        roleMap.put("UX Designer", Arrays.asList("ux designer", "user experience designer"));
        roleMap.put("Product Designer", Arrays.asList("product designer", "interaction designer"));
        roleMap.put("Product Manager", Arrays.asList("product manager", "pm", "associate product manager"));
        roleMap.put("Business Analyst", Arrays.asList("business analyst", "ba"));
        roleMap.put("Technical Writer", Arrays.asList("technical writer", "documentation specialist"));
        roleMap.put("HR", Arrays.asList("hr", "human resources", "recruiter", "talent acquisition"));
        roleMap.put("Marketing", Arrays.asList("marketing", "digital marketing", "seo specialist"));
        roleMap.put("Finance", Arrays.asList("finance", "financial analyst", "accountant"));
        roleMap.put("Sales", Arrays.asList("sales", "account executive", "business development"));
        roleMap.put("Mechanical", Arrays.asList("mechanical engineer", "mechanical design"));
        roleMap.put("Civil", Arrays.asList("civil engineer", "structural engineer"));
        roleMap.put("Electrical", Arrays.asList("electrical engineer"));
        roleMap.put("Embedded", Arrays.asList("embedded systems", "embedded software", "firmware"));
        roleMap.put("IoT", Arrays.asList("iot engineer", "internet of things"));
        roleMap.put("Research", Arrays.asList("researcher", "research scientist", "research assistant"));
        roleMap.put("Academic", Arrays.asList("professor", "teacher", "lecturer", "academic"));

        String bestRole = "General Resume";
        int maxScore = 0;

        for (Map.Entry<String, List<String>> entry : roleMap.entrySet()) {
            int score = 0;
            for (String term : entry.getValue()) {
                if (lowerDes.contains(term)) {
                    score += 15;
                }
                for (String sk : skills) {
                    if (sk.contains(term)) {
                        score += 3;
                    }
                }
                if (summary.contains(term)) {
                    score += 2;
                }
            }
            if (score > maxScore) {
                maxScore = score;
                bestRole = entry.getKey();
            }
        }

        if (maxScore < 10) {
            return "General Resume";
        }

        return bestRole;
    }
}
