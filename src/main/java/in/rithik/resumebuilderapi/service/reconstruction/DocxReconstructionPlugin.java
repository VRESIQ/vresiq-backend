package in.rithik.resumebuilderapi.service.reconstruction;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.awt.image.BufferedImage;

@Component
public class DocxReconstructionPlugin implements ReconstructionPlugin {

    @Override
    public boolean supports(String mimeType) {
        return "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equalsIgnoreCase(mimeType);
    }

    @Override
    public String extractText(InputStream inputStream) throws Exception {
        try (XWPFDocument document = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    /**
     * Returns the first picture in the document body that is large enough to be a portrait.
     * null means no usable picture was found; it is never treated as "no photo exists".
     */
    @Override
    public byte[] extractPortrait(InputStream inputStream) {
        try {
            byte[] bytes = inputStream.readAllBytes();
            try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
                for (XWPFParagraph paragraph : document.getParagraphs()) {
                    for (XWPFRun run : paragraph.getRuns()) {
                        for (XWPFPicture picture : run.getEmbeddedPictures()) {
                            byte[] data = picture.getPictureData() == null ? null : picture.getPictureData().getData();
                            if (data == null || data.length < 1200) continue;
                            BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
                            if (image == null) continue;
                            int width = image.getWidth();
                            int height = image.getHeight();
                            if (width < 48 || height < 48 || width > 3000 || height > 3000) continue;
                            double ratio = (double) width / (double) height;
                            if (ratio < 0.55 || ratio > 1.75) continue;
                            ByteArrayOutputStream out = new ByteArrayOutputStream();
                            ImageIO.write(image, "png", out);
                            return out.toByteArray();
                        }
                    }
                }
            }
        } catch (Exception exception) {
            return null;
        }
        return null;
    }
}
