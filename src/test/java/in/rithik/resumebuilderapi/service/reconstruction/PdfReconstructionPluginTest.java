package in.rithik.resumebuilderapi.service.reconstruction;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.rithik.resumebuilderapi.document.Resume;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;

class PdfReconstructionPluginTest {
    private final PdfReconstructionPlugin plugin = new PdfReconstructionPlugin();

    @Test
    void extractsTextAcrossFivePagesAndKeepsAnnotationLinks() throws Exception {
        byte[] pdf = createPdf(5, true);
        try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            assertEquals(5, document.getNumberOfPages());
        }

        String text = plugin.extractText(new ByteArrayInputStream(pdf));
        assertTrue(text.contains("Regression page 1"));
        assertTrue(text.contains("Regression page 5"));
        assertTrue(text.contains("https://portfolio.example.test/projects"));
    }

    @Test
    void extractsShortOnePageResumeText() throws Exception {
        byte[] pdf = createPdf(1, false);
        String text = plugin.extractText(new ByteArrayInputStream(pdf));
        assertTrue(text.contains("Regression page 1"));
        assertFalse(text.contains("Regression page 2"));
    }

    @Test
    void writesSyntheticFivePageEvidenceWhenOutputDirectoryIsConfigured() throws Exception {
        String configuredDirectory = System.getProperty("reconstruction.evidence.dir");
        Assumptions.assumeTrue(configuredDirectory != null && !configuredDirectory.isBlank());
        Path directory = Path.of(configuredDirectory);
        Files.createDirectories(directory);

        byte[] inputPdf = createResumeFixture();
        Files.write(directory.resolve("synthetic-five-page-original.pdf"), inputPdf);
        Files.write(directory.resolve("synthetic-short-original.pdf"), createPdf(1, false));
        try (PDDocument sourceDocument = org.apache.pdfbox.Loader.loadPDF(inputPdf)) {
            ImageIO.write(new PDFRenderer(sourceDocument).renderImageWithDPI(0, 110), "png",
                    directory.resolve("synthetic-original-page-1.png").toFile());
        }
        String extractedText = plugin.extractText(new ByteArrayInputStream(inputPdf));
        DeterministicParser.ParseResult parsed = new DeterministicParser().parseWithDiagnostics(extractedText);
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(directory.resolve("synthetic-extracted-resume.json").toFile(), parsed.getResume());
        Files.writeString(directory.resolve("synthetic-extracted-text.txt"), extractedText);
        Files.writeString(directory.resolve("synthetic-review-warnings.txt"), String.join("\n", parsed.getWarnings()));

        try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(inputPdf)) {
            assertEquals(5, document.getNumberOfPages());
        }
        Resume resume = parsed.getResume();
        assertNotNull(resume.getProfileInfo());
        assertEquals(1, resume.getWorkExperience().size());
        assertEquals(1, resume.getEducation().size());
        assertFalse(resume.getProjects().isEmpty());
        assertEquals(1, resume.getCertifications().size());
        assertTrue(resume.getCustomSections().containsKey("publications"));
    }

    @Test
    void verifiesGeneratedPdfPageCountAndContentWhenOutputPathIsConfigured() throws Exception {
        String configuredPdf = System.getProperty("reconstruction.exported.pdf");
        Assumptions.assumeTrue(configuredPdf != null && !configuredPdf.isBlank());
        byte[] bytes = Files.readAllBytes(Path.of(configuredPdf));
        try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(bytes)) {
            assertEquals(1, document.getNumberOfPages(), "controlled reconstruction should fit on one page");
        }
        String text = plugin.extractText(new ByteArrayInputStream(bytes));
        assertTrue(text.contains("Morgan Example"));
        assertTrue(text.contains("Product engineer with 12 years"));
        assertTrue(text.contains("Northstar Systems"));
        assertTrue(text.contains("11 services"));
        assertTrue(text.contains("Guelph University"));
        assertTrue(text.contains("Cloud Architect"));
        assertTrue(text.contains("Reliable Streams in Production"));
        assertFalse(text.contains("Made with VRESIQ"), "premium export must not be watermarked");
    }

    @Test
    void verifiesFreePlanWatermarkInGeneratedPdfWhenConfigured() throws Exception {
        String configuredPdf = System.getProperty("reconstruction.free.pdf");
        Assumptions.assumeTrue(configuredPdf != null && !configuredPdf.isBlank());
        byte[] bytes = Files.readAllBytes(Path.of(configuredPdf));
        try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(bytes)) {
            assertEquals(1, document.getNumberOfPages());
        }
        String text = plugin.extractText(new ByteArrayInputStream(bytes));
        assertTrue(text.contains("Made with VRESIQ"));
    }

    private byte[] createResumeFixture() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            addTextPage(document, List.of("Morgan Example", "morgan@example.test | +1 416 555 0101", "https://morgan.example.test",
                    "SUMMARY", "Product engineer with 12 years of experience building reliable systems."));
            addTextPage(document, List.of("EXPERIENCE", "Senior Engineer | Northstar Systems | Jan 2020 - Present",
                    "- Led migration of 11 services to a shared platform.", "- Reduced build time by 32 percent."));
            addTextPage(document, List.of("EDUCATION", "BSc Computer Science | Guelph University | 2010 - 2014",
                    "PROJECTS", "Maple Analytics", "- Built a regional analytics platform."));
            addTextPage(document, List.of("CERTIFICATIONS", "Cloud Architect, Northstar Institute, 2021",
                    "PUBLICATIONS", "Reliable Streams in Production, Journal of Systems, 2022"));
            addTextPage(document, List.of("SKILLS", "Java, Kubernetes, Cloud Architecture", "LANGUAGES", "English (Fluent)",
                    "https://github.com/morgan-example"));
            document.save(output);
            return output.toByteArray();
        }
    }

    private void addTextPage(PDDocument document, List<String> lines) throws Exception {
        PDPage page = new PDPage(PDRectangle.LETTER);
        document.addPage(page);
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            stream.beginText();
            stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
            stream.newLineAtOffset(54, 730);
            for (String line : lines) {
                stream.showText(line);
                stream.newLineAtOffset(0, -18);
            }
            stream.endText();
        }
    }

    private byte[] createPdf(int pageCount, boolean addLink) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int pageNumber = 1; pageNumber <= pageCount; pageNumber++) {
                PDPage page = new PDPage(PDRectangle.LETTER);
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(72, 720);
                    stream.showText("Regression page " + pageNumber + " includes preserved source text.");
                    stream.endText();
                }
                if (addLink && pageNumber == 3) {
                    PDAnnotationLink annotation = new PDAnnotationLink();
                    annotation.setRectangle(new PDRectangle(72, 680, 180, 20));
                    PDActionURI action = new PDActionURI();
                    action.setURI("https://portfolio.example.test/projects");
                    annotation.setAction(action);
                    page.getAnnotations().add(annotation);
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
