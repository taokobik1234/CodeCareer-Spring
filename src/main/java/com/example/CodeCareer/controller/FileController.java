package com.example.CodeCareer.controller;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.CodeCareer.domain.response.file.ResUploadFileDTO;
import com.example.CodeCareer.service.FileService;
import com.example.CodeCareer.util.annotation.ApiMessage;
import com.example.CodeCareer.util.error.StorageException;

@RestController
@RequestMapping("/api/v1")
public class FileController {

    @Value("${thienvo.upload-file.base-uri}")
    private String baseURI;

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/files")
    @ApiMessage("Upload single file")
    public ResponseEntity<ResUploadFileDTO> upload(
            @RequestParam(name = "file", required = false) MultipartFile file,
            @RequestParam("folder") String folder
    ) throws IOException, StorageException {
        if (file == null || file.isEmpty()) {
            throw new StorageException("File is empty. Please upload a file.");
        }
        String fileName = file.getOriginalFilename();
        List<String> allowedExtensions = Arrays.asList("pdf", "jpg", "jpeg", "png", "doc", "docx");
        boolean isValid = allowedExtensions.stream().anyMatch(item -> fileName.toLowerCase().endsWith(item));

        if (!isValid) {
            throw new StorageException("Invalid file extension. Only allows " + allowedExtensions.toString());
        }

        this.fileService.createDirectory(folder); // Chỉ in log, không tạo local
        String uploadedFileUrl = this.fileService.store(file, folder); // Lấy URL từ Cloudinary
        ResUploadFileDTO res = new ResUploadFileDTO(uploadedFileUrl, Instant.now());

        return ResponseEntity.ok().body(res);
    }
//
//    @GetMapping("/files")
//    @ApiMessage("Download a file")
//    public ResponseEntity<Resource> download(
//            @RequestParam(name = "fileName", required = false) String fileName,
//            @RequestParam(name = "folder", required = false) String folder)
//            throws StorageException, URISyntaxException, FileNotFoundException {
//        if (fileName == null || folder == null) {
//            throw new StorageException("Missing required params : (fileName or folder) in query params.");
//        }
//
//        // check file exist (and not a directory)
//        long fileLength = this.fileService.getFileLength(fileName, folder);
//        if (fileLength == 0) {
//            throw new StorageException("File with name = " + fileName + " not found.");
//        }
//
//        // download a file
//        InputStreamResource resource = this.fileService.getResource(fileName, folder);
//
//        return ResponseEntity.ok()
//                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
//                .contentLength(fileLength)
//                .contentType(MediaType.APPLICATION_OCTET_STREAM)
//                .body(resource);
//    }
}
