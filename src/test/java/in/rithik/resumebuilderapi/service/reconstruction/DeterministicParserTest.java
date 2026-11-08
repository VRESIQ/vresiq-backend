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
        assertEquals(2, resume.getProjects().size());
        assertEquals(1, resume.getCertifications().size());
        assertNull(resume.getCustomSections().get("publications").get(0).getDate());
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
}
