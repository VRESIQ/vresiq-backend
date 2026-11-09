package in.rithik.resumebuilderapi.service.reconstruction;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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

    /**
     * Extracts the most likely portrait from page 1.
     *
     * <p>Heuristic: the largest embedded bitmap on the first page that is roughly
     * portrait/square and large enough to be a photo. Decorative rules, icons and logos are
     * skipped by the size and aspect-ratio bounds. Text-only PDFs and vector "photos" return
     * null; a null result means "not extractable", not "no photo exists".
     */
    @Override
    public byte[] extractPortrait(InputStream inputStream) {
        try {
            byte[] bytes = inputStream.readAllBytes();
            try (PDDocument document = Loader.loadPDF(bytes)) {
                if (document.getNumberOfPages() == 0) return null;
                PDPage page = document.getPage(0);
                PDResources resources = page.getResources();
                if (resources == null) return null;

                PDImageXObject best = null;
                long bestArea = 0;
                for (COSName name : resources.getXObjectNames()) {
                    PDXObject object = resources.getXObject(name);
                    if (!(object instanceof PDImageXObject image)) continue;
                    int width = image.getWidth();
                    int height = image.getHeight();
                    if (width < 48 || height < 48 || width > 3000 || height > 3000) continue;
                    double ratio = (double) width / (double) height;
                    if (ratio < 0.55 || ratio > 1.75) continue;
                    long area = (long) width * height;
                    if (area > bestArea) {
                        bestArea = area;
                        best = image;
                    }
                }
                if (best == null) return null;

                BufferedImage rendered = best.getImage();
                if (rendered == null || rendered.getWidth() < 48 || rendered.getHeight() < 48) return null;
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(rendered, "png", out);
                return out.toByteArray();
            }
        } catch (Exception exception) {
            // Portrait extraction is additive: never fail an otherwise valid reconstruction.
            return null;
        }
    }
}
