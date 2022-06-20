package com.axelor.testing;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.time.Instant;
import javax.imageio.ImageIO;

public class GoogleZxigQRCodeGenerator {
  private static String QRCODE_PATH = "/home/axelor/Downloads/";

  public String writeQRCode() throws Exception {
    Instant now = Instant.now();
    String qrcode = QRCODE_PATH + (now.getEpochSecond()) + "My-QRCODE.png";
    QRCodeWriter writer = new QRCodeWriter();
    BitMatrix bitMatrix = writer.encode("Hello Bikash", BarcodeFormat.QR_CODE, 350, 350);
    Path path = FileSystems.getDefault().getPath(qrcode);
    MatrixToImageWriter.writeToPath(bitMatrix, "PNG", path);
    return "QRCODE is generated successfully....";
  }

  public String readQRCode(String qrcodeImage) throws Exception {
    BufferedImage bufferedImage = ImageIO.read(new File(qrcodeImage));
    LuminanceSource luminanceSource = new BufferedImageLuminanceSource(bufferedImage);
    BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(luminanceSource));
    Result result = new MultiFormatReader().decode(binaryBitmap);
    return result.getText();
  }

  public static void main(String[] args) throws Exception {
    GoogleZxigQRCodeGenerator codeGenerator = new GoogleZxigQRCodeGenerator();
    //    For create QR Code
    //    System.out.println(codeGenerator.writeQRCode());
    //    For read Code
    System.out.println(codeGenerator.readQRCode(QRCODE_PATH + "1654068070My-QRCODE.png"));
  }
}
