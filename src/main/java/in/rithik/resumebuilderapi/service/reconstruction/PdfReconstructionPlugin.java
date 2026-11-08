package in.rithik.resumebuilderapi.service.reconstruction;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class PdfReconstructionPlugin implements ReconstructionPlugin {

    @Override
    public boolean supports(String mimeType) {
        return "application/pdf".equalsIgnoreCase(mimeType);
    }

    @Override
    public String extractText(InputStream inputStream) throws Exception {
        byte[] bytes = inputStream.readAllBytes();
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            // Sorting is a best-effort reading order for positioned PDF text. It cannot
            // reliably reconstruct arbitrary columns, so that limitation is surfaced upstream.
            stripper.setSortByPosition(true);
            String extracted = stripper.getText(document);
            Set<String> links = new LinkedHashSet<>();
            for (PDPage page : document.getPages()) {
                for (PDAnnotation annotation : page.getAnnotations()) {
                    if (annotation instanceof PDAnnotationLink link && link.getAction() instanceof PDActionURI uri
                            && uri.getURI() != null && !uri.getURI().isBlank()) {
                        links.add(uri.getURI().trim());
                    }
                }
            }
            String source = extracted == null ? "" : extracted;
            StringBuilder output = new StringBuilder(source);
            String lowerSource = source.toLowerCase(java.util.Locale.ROOT);
            for (String link : links) {
                if (!lowerSource.contains(link.toLowerCase(java.util.Locale.ROOT))) {
                    if (output.length() > 0) output.append('\n');
                    output.append(link);
                }
            }
            return output.toString();
        }
    }
}
