package hei.school.sarisary.endpoint.rest.controller.health;

import static java.io.File.createTempFile;
import static java.nio.file.Files.createTempDirectory;
import static java.util.UUID.randomUUID;
import static school.hei.sarisary.file.FileHashAlgorithm.NONE;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import school.hei.sarisary.PojaGenerated;
import school.hei.sarisary.file.BucketComponent;
import school.hei.sarisary.file.FileHash;

import javax.imageio.ImageIO;

@PojaGenerated
@RestController
@AllArgsConstructor
public class HealthBucketController {

  BucketComponent bucketComponent;

  private static final String HEALTH_KEY = "health/";

  @GetMapping(value = "/health/bucket")
  public ResponseEntity<String> file_can_be_uploaded_then_signed() throws IOException {
    var fileSuffix = ".txt";
    var filePrefix = randomUUID().toString();
    var fileToUpload = createTempFile(filePrefix, fileSuffix);
    writeRandomContent(fileToUpload);
    var fileBucketKey = HEALTH_KEY + filePrefix + fileSuffix;
    can_upload_file_then_download_file(fileToUpload, fileBucketKey);

    var directoryPrefix = "dir-" + randomUUID();
    var directoryToUpload = createTempDirectory(directoryPrefix).toFile();
    var fileInDirectory =
        new File(directoryToUpload.getAbsolutePath() + "/" + randomUUID() + ".txt");
    writeRandomContent(fileInDirectory);
    var directoryBucketKey = HEALTH_KEY + directoryPrefix;
    can_upload_directory(directoryToUpload, directoryBucketKey);

    return ResponseEntity.of(Optional.of(can_presign(fileBucketKey).toString()));
  }

  private void writeRandomContent(File file) throws IOException {
    FileWriter writer = new FileWriter(file);
    var content = randomUUID().toString();
    writer.write(content);
    writer.close();
  }

  private File can_upload_file_then_download_file(File toUpload, String bucketKey)
      throws IOException {
    bucketComponent.upload(toUpload, bucketKey);

    var downloaded = bucketComponent.download(bucketKey);
    var downloadedContent = Files.readString(downloaded.toPath());
    var uploadedContent = Files.readString(toUpload.toPath());
    if (!uploadedContent.equals(downloadedContent)) {
      throw new RuntimeException("Uploaded and downloaded contents mismatch");
    }

    return downloaded;
  }

  private FileHash can_upload_directory(File toUpload, String bucketKey) {
    var hash = bucketComponent.upload(toUpload, bucketKey);
    if (!NONE.equals(hash.algorithm())) {
      throw new RuntimeException("FileHashAlgorithm.NONE expected but got: " + hash.algorithm());
    }
    return hash;
  }

  private URL can_presign(String fileBucketKey) {
    return bucketComponent.presign(fileBucketKey, Duration.ofMinutes(2));
  }

  @PutMapping(value = "/black-and-white/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.IMAGE_JPEG_VALUE)
  public ResponseEntity<String> uploadToBucket(
          @RequestBody MultipartFile img,
          @PathVariable String id
  ) throws IOException {
    File localFile = convertMultipartFileToFile(img);
    bucketComponent.upload(localFile, HEALTH_KEY + id);
    localFile.delete();
    return ResponseEntity.ok("Pictures uploaded");
  }


  @GetMapping("/black-and-white/{id}")
  public ResponseEntity<byte[]> getPicturesBlackAndWhite(
          @PathVariable String id
  ){
    byte[] originalPictureByte = bucketComponent.download(HEALTH_KEY+id).toString().getBytes();

    byte[] blackAndWhitePictureByte = convertToBW(originalPictureByte);

    return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(blackAndWhitePictureByte);
  }

  private File convertMultipartFileToFile(MultipartFile file) throws IOException {
    File localFile = File.createTempFile("tmp", file.getOriginalFilename());
    file.transferTo(localFile);
    return localFile;
  }

  private byte[] convertToBW(byte[] originalPictureByte){
    try {
      ByteArrayInputStream bais = new ByteArrayInputStream(originalPictureByte);
      BufferedImage originalImage = ImageIO.read(bais);

      BufferedImage BWImage = new BufferedImage(
              originalImage.getWidth(),
              originalImage.getHeight(),
              BufferedImage.TYPE_BYTE_GRAY
      );
      Graphics g = BWImage.getGraphics();
      g.drawImage(originalImage, 0, 0, null);

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ImageIO.write(BWImage, "jpg", baos);

      return baos.toByteArray();
    }catch (IOException e) {
      e.printStackTrace();
      return originalPictureByte;
    }
  }
}
