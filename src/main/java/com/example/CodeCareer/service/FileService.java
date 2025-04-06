package com.example.CodeCareer.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

@Service
public class FileService {

    private final Cloudinary cloudinary;

    @Value("${cloudinary.upload-preset}")
    private String uploadPreset;

    public FileService(
            @Value("${cloudinary.cloud-name}") String cloudName,
            @Value("${cloudinary.api-key}") String apiKey,
            @Value("${cloudinary.api-secret}") String apiSecret) {
        Map<String, String> config = new HashMap<>();
        config.put("cloud_name", cloudName);
        config.put("api_key", apiKey);
        config.put("api_secret", apiSecret);
        this.cloudinary = new Cloudinary(config);
    }

    public void createDirectory(String folder) {
        // Không cần tạo thư mục local nữa, Cloudinary tự quản lý folder
        System.out.println(">>> Folder " + folder + " will be used in Cloudinary");
    }

    public String store(MultipartFile file, String folder) throws IOException {
        Map<String, Object> uploadParams = new HashMap<>();
        uploadParams.put("folder", folder); // Lưu file vào folder trên Cloudinary
        uploadParams.put("public_id", System.currentTimeMillis() + "-" + file.getOriginalFilename());
        uploadParams.put("upload_preset", uploadPreset); // Dùng unsigned preset

        Map uploadResult = cloudinary.uploader().upload(file.getBytes(), uploadParams);
        return (String) uploadResult.get("secure_url"); // Trả về URL của file
    }

    // Các phương thức khác (getResource, getFileLength) cần sửa nếu bạn muốn tải file từ Cloudinary
    public Resource getResource(String fileUrl) throws IOException {
        URL url = new URL(fileUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        InputStream inputStream = connection.getInputStream();
        return new InputStreamResource(inputStream);
    }

    // Phương thức để lấy kích thước file (nếu cần)
    public long getFileLength(String fileUrl) throws IOException {
        URL url = new URL(fileUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("HEAD"); // Chỉ lấy header để kiểm tra kích thước
        return connection.getContentLengthLong();
    }
}