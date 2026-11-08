package in.rithik.resumebuilderapi.service.reconstruction;

import in.rithik.resumebuilderapi.document.Resume;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeterministicParserTest {
    private final DeterministicParser parser = new DeterministicParser();

    @Test
    void retainsSourceFactsAndMapsProfessionalSectionsWithoutInventingDates() {
        String source = """
                Jordan Tester
                jordan.tester@example.test
                +1 416 555 0134
                https://www.linkedin.com/in/jordan-tester

                SUMMARY
                Product engineer with 12 years of experience building distributed systems.

                EXPERIENCE
                Senior Engineer | Northstar Systems | Jan 2020 - Present
                • Led migration of 11 services to the new platform.
                This experience description includes the word experience but is not a heading.

                EDUCATION
                BSc Computer Science | University of Guelph | 2010 - 2014

                PROJECTS
                Maple Analytics
                • Built a data pipeline for 40 regions.

                CERTIFICATIONS
                Cloud Architect, Northstar Institute, 2021

                PUBLICATIONS
                Reliable Streams in Production, Journal of Systems, 2022

                SKILLS
                Java, Kubernetes, Java

                LANGUAGES
                English (Fluent)

                AWARDS
                2022 Engineering Impact Award
                """;

        DeterministicParser.ParseResult result = parser.parseWithDiagnostics(source);
        Resume resume = result.getResume();

        assertEquals("Jordan Tester", resume.getProfileInfo().getFullName());
        assertEquals("Product engineer with 12 years of experience building distributed systems.", resume.getProfileInfo().getSummary());
        assertEquals("jordan.tester@example.test", resume.getContactInfo().getEmail());
        assertEquals("+1 416 555 0134", resume.getContactInfo().getPhone());
        assertEquals("Senior Engineer", resume.getWorkExperience().get(0).getRole());
        assertEquals("Northstar Systems", resume.getWorkExperience().get(0).getCompany());
        assertEquals("Jan 2020", resume.getWorkExperience().get(0).getStartDate());
        assertTrue(resume.getWorkExperience().get(0).getDescription().contains("11 services"));
        assertTrue(resume.getWorkExperience().get(0).getDescription().contains("is not a heading"));
        assertEquals("University of Guelph", resume.getEducation().get(0).getInstitution());
        assertEquals("2010", resume.getEducation().get(0).getStartDate());
        assertEquals("2014", resume.getEducation().get(0).getEndDate());
        assertEquals(1, resume.getProjects().size());
        assertEquals("Maple Analytics", resume.getProjects().get(0).getTitle());
        assertTrue(resume.getProjects().get(0).getDescription().contains("Built a data pipeline"));
        assertEquals(1, resume.getCertifications().size());
        assertEquals("2022", resume.getCustomSections().get("publications").get(0).getDate());
        assertEquals("Journal of Systems", resume.getCustomSections().get("publications").get(0).getSubtitle());
        assertEquals(3, resume.getSkills().size(), "duplicate skills must stay in the model for review");
        assertEquals("English (Fluent)", resume.getLanguages().get(0).getName());
        assertTrue(result.getWarnings().stream().anyMatch(warning -> warning.contains("duplicate skill")));
        assertFalse(result.getUnclassifiedContent().stream().anyMatch(line -> line.contains("experience description")));
    }

    @Test
    void combinesRepeatedSectionContentAndFlagsItForReview() {
        DeterministicParser.ParseResult result = parser.parseWithDiagnostics("""
                Casey Example
                SUMMARY
                First source sentence.
                EXPERIENCE
                Developer | Example Co | 2021 - Present
                Built a service.
                SUMMARY
                Second source sentence.
                """);

        assertEquals("First source sentence.\nSecond source sentence.", result.getResume().getProfileInfo().getSummary());
        assertTrue(result.getWarnings().stream().anyMatch(warning -> warning.startsWith("Repeated 'summary'")));
    }

    @Test
    void emptyOrImageOnlyExtractionDoesNotCreateCandidateOrFacts() {
        DeterministicParser.ParseResult result = parser.parseWithDiagnostics("\n  \n");

        assertNull(result.getResume().getProfileInfo());
        assertTrue(result.getWarnings().stream().anyMatch(warning -> warning.contains("OCR is not configured")));
        assertTrue(result.getUnclassifiedContent().isEmpty());
    }

    @Test
    void deDuplicatesRepeatedWebsiteUrlsAndFlagsThemWithoutLosingTheDestination() {
        DeterministicParser.ParseResult result = parser.parseWithDiagnostics("""
                Taylor Example
                https://taylor.example.test/work
                https://taylor.example.test/work
                SKILLS
                Java
                """);

        assertEquals("https://taylor.example.test/work", result.getResume().getContactInfo().getWebsite());
        assertTrue(result.getWarnings().stream().anyMatch(warning -> warning.contains("Repeated URL")));
        assertTrue(result.getResume().getCustomSections() == null || !result.getResume().getCustomSections().containsKey("links"));
    }

    @Test
    void keepsPdfAnnotationsAndPageFootersOutOfSectionsAndRepairsVerifiedLayoutArtifacts() {
        DeterministicParser.ParseResult result = parser.parseWithDiagnostics("""
                Jordan Tester
                Software Developer Analyst
                jordan.tester@example.test | +1 416 555 0134 | LinkedIn: linkedin.com/in/www.linkedin.com/in/jordan-tester
                SUMMARY
                Analyzed datasetsincluding patient records and claimsto identify patterns.
                EXPERIENCE
                Analyst | Example Health | 2021 - 2025
                Reviewed source records.
                PROJECTS
                Project Alpha
                A project summary continued across the extracted lines.
                Website ↗
                EDUCATION
                Example University Toronto
                Bachelor of Pharmacy Sep 2012 - May 2016
                CERTIFICATIONS
                Data Analyst, Example Institute, May 2022
                PUBLICATIONS
                Clinical Study Title
                European Respiratory Journal • November 2019
                Paper Link ↗
                Made with VRESIQ
                https://www.linkedin.com/in/jordan-tester
                https://publications.example.test/paper
                https://www.google.com/maps/search/?api=1&query=Toronto%2C%20Canada
                mailto:jordan.tester@example.test
                tel:+14165550134
                """);

        Resume resume = result.getResume();
        assertEquals("Software Developer Analyst", resume.getProfileInfo().getDesignation());
        assertEquals("https://linkedin.com/in/jordan-tester", resume.getContactInfo().getLinkedIn());
        assertEquals("Toronto, Canada", resume.getContactInfo().getLocation());
        assertTrue(resume.getProfileInfo().getSummary().contains("datasets including"));
        assertTrue(resume.getProfileInfo().getSummary().contains("claims to"));
        assertEquals(1, resume.getProjects().size());
        assertTrue(resume.getProjects().get(0).getDescription().contains("continued across the extracted lines"));
        assertEquals(1, resume.getEducation().size());
        assertEquals("Example University Toronto", resume.getEducation().get(0).getInstitution());
        assertEquals("Bachelor of Pharmacy", resume.getEducation().get(0).getDegree());
        assertEquals("Sep 2012", resume.getEducation().get(0).getStartDate());
        assertEquals("May 2016", resume.getEducation().get(0).getEndDate());
        assertEquals("Example Institute", resume.getCertifications().get(0).getIssuer());
        assertEquals("May 2022", resume.getCertifications().get(0).getYear());
        assertEquals(1, resume.getCustomSections().get("publications").size());
        assertEquals("European Respiratory Journal", resume.getCustomSections().get("publications").get(0).getSubtitle());
        assertEquals("November 2019", resume.getCustomSections().get("publications").get(0).getDate());
        assertEquals("https://publications.example.test/paper", resume.getCustomSections().get("publications").get(0).getPaperUrl());
        assertTrue(resume.getCustomSections().get("publications").get(0).getDescription() == null);
        assertTrue(result.getWarnings().stream().anyMatch(warning -> warning.contains("watermark")));
        assertTrue(result.getWarnings().stream().anyMatch(warning -> warning.contains("Repeated URLs")));
        assertFalse(result.getResume().getCustomSections().get("publications").stream()
                .anyMatch(entry -> entry.getTitle().startsWith("mailto:") || entry.getTitle().startsWith("tel:")));
    }
}
