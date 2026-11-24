package com.girlshop.utils;
import java.awt.image.BufferedImage;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.web.multipart.MultipartFile;

public class ImageStorage {
    public static Path ownedPath(Path base, Long userId, String name) throws IOException {
        String relative = name == null ? "" : name.replace('\\', '/');
        if (relative.startsWith("/imgs/")) relative = relative.substring(6);
        if (relative.startsWith("imgs/")) relative = relative.substring(5);
        if (!relative.matches("products/" + userId + "/[a-f0-9-]{36}\\.(jpg|png)"))
            throw new IllegalArgumentException("只能删除自己上传的图片");
        Path root = base.toRealPath(), target = root.resolve(relative).normalize();
        if (!target.startsWith(root) || !target.getParent().toRealPath().startsWith(root)
                || Files.isSymbolicLink(target)) throw new IllegalArgumentException("非法图片路径");
        return target;
    }
    public static String save(Path base, Long userId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty() || file.getSize() > 10 * 1024 * 1024)
            throw new IllegalArgumentException("图片不能为空且不能超过10MB");
        BufferedImage image;
        String format;
        try (InputStream in = file.getInputStream(); ImageInputStream input = ImageIO.createImageInputStream(in)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("文件不是有效图片");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!(format.equals("jpeg") || format.equals("jpg") || format.equals("png")))
                    throw new IllegalArgumentException("只支持 JPG 和 PNG 图片");
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w < 1 || h < 1 || w > 6000 || h > 6000 || (long)w * h > 16000000)
                    throw new IllegalArgumentException("图片尺寸过大");
                image = reader.read(0);
            } finally { reader.dispose(); }
        }
        String suffix = format.equals("png") ? "png" : "jpg";
        String relative = "products/" + userId + "/" + UUID.randomUUID() + "." + suffix;
        Path target = base.resolve(relative);
        Files.createDirectories(target.getParent());
        if (!ImageIO.write(image, suffix, target.toFile())) throw new IOException("图片编码失败");
        return "/imgs/" + relative;
    }
}
