package com.lzbsdsg.stocksimulation.user.infrastructure.storage;

import com.lzbsdsg.stocksimulation.common.exception.BizException;
import com.lzbsdsg.stocksimulation.common.result.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** 头像文件存储服务（本地磁盘）。 */
@Service
public class AvatarStorageService {

  private static final long DEFAULT_MAX_SIZE_BYTES = 2L * 1024 * 1024;
  private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");
  private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

  @Value("${avatar.storage.root-dir:uploads}")
  private String storageRootDir;

  @Value("${avatar.storage.public-prefix:/uploads}")
  private String publicPrefix;

  @Value("${avatar.storage.max-size-bytes:2097152}")
  private long maxSizeBytes;

  public String store(MultipartFile file, Long userId) {
    validate(file);

    String extension = resolveExtension(file);
    String monthFolder = LocalDate.now().format(MONTH_FORMATTER);
    Path root = Paths.get(storageRootDir).toAbsolutePath().normalize();
    Path avatarDir = root.resolve("avatars").resolve(monthFolder);

    String fileName =
        "u" + userId + "-" + UUID.randomUUID().toString().replace("-", "") + "." + extension;
    Path target = avatarDir.resolve(fileName).normalize();

    try {
      Files.createDirectories(avatarDir);
      file.transferTo(target.toFile());
    } catch (IOException ex) {
      throw new BizException(ErrorCode.INTERNAL_ERROR, "头像上传失败");
    }

    return normalizePublicPath(publicPrefix) + "/avatars/" + monthFolder + "/" + fileName;
  }

  private void validate(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new BizException(ErrorCode.BAD_REQUEST, "头像文件不能为空");
    }
    long sizeLimit = maxSizeBytes > 0 ? maxSizeBytes : DEFAULT_MAX_SIZE_BYTES;
    if (file.getSize() > sizeLimit) {
      throw new BizException(ErrorCode.BAD_REQUEST, "头像大小不能超过2MB");
    }
    String contentType = file.getContentType();
    if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
      throw new BizException(ErrorCode.BAD_REQUEST, "头像仅支持图片文件");
    }
  }

  private String resolveExtension(MultipartFile file) {
    String original = file.getOriginalFilename();
    if (original != null && original.contains(".")) {
      String ext = original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
      if (ALLOWED_EXTENSIONS.contains(ext)) {
        return ext;
      }
    }

    String contentType = file.getContentType();
    if ("image/jpeg".equalsIgnoreCase(contentType)) {
      return "jpg";
    }
    if ("image/png".equalsIgnoreCase(contentType)) {
      return "png";
    }
    if ("image/webp".equalsIgnoreCase(contentType)) {
      return "webp";
    }
    if ("image/gif".equalsIgnoreCase(contentType)) {
      return "gif";
    }

    throw new BizException(ErrorCode.BAD_REQUEST, "头像格式仅支持 jpg/jpeg/png/webp/gif");
  }

  private String normalizePublicPath(String prefix) {
    if (prefix == null || prefix.isBlank()) {
      return "/uploads";
    }
    if (prefix.endsWith("/")) {
      return prefix.substring(0, prefix.length() - 1);
    }
    return prefix;
  }
}
