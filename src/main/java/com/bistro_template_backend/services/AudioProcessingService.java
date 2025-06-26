package com.bistro_template_backend.services;

import com.bistro_template_backend.config.VoiceAIConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.tika.Tika;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AudioProcessingService {

    private final VoiceAIConfig voiceAIConfig;
    private final Tika tika = new Tika();

    /**
     * Validate and process audio file before sending to OpenAI
     */
    public ProcessedAudioFile processAudioFile(MultipartFile audioFile) throws Exception {
        log.info("Processing audio file: {} ({})", audioFile.getOriginalFilename(), 
                FileUtils.byteCountToDisplaySize(audioFile.getSize()));

        // Validate file
        validateAudioFile(audioFile);

        // Detect actual file type
        String detectedMimeType = tika.detect(audioFile.getInputStream());
        log.debug("Detected MIME type: {}", detectedMimeType);

        // Create temporary file
        File tempFile = createTempFile(audioFile);

        // Get audio metadata
        AudioMetadata metadata = extractAudioMetadata(tempFile);
        log.info("Audio metadata - Duration: {}s, Format: {}, Size: {}", 
                metadata.getDurationSeconds(), metadata.getFormat(), 
                FileUtils.byteCountToDisplaySize(tempFile.length()));

        // Validate duration
        if (metadata.getDurationSeconds() > voiceAIConfig.getMaxAudioDuration()) {
            tempFile.delete();
            throw new IllegalArgumentException(
                String.format("Audio duration (%.1fs) exceeds maximum allowed duration (%ds)", 
                        metadata.getDurationSeconds(), voiceAIConfig.getMaxAudioDuration())
            );
        }

        // Convert if necessary
        File processedFile = optimizeAudioForWhisper(tempFile, metadata);

        return ProcessedAudioFile.builder()
                .originalFile(tempFile)
                .processedFile(processedFile)
                .metadata(metadata)
                .tempFiles(Arrays.asList(tempFile, processedFile))
                .build();
    }

    /**
     * Validate audio file format, size, and content
     */
    public void validateAudioFile(MultipartFile audioFile) throws Exception {
        if (audioFile == null || audioFile.isEmpty()) {
            throw new IllegalArgumentException("Audio file is required and cannot be empty");
        }

        // Check file size
        if (audioFile.getSize() > voiceAIConfig.getMaxFileSizeBytes()) {
            throw new IllegalArgumentException(
                String.format("File size (%s) exceeds maximum allowed size (%s)",
                        FileUtils.byteCountToDisplaySize(audioFile.getSize()),
                        FileUtils.byteCountToDisplaySize(voiceAIConfig.getMaxFileSizeBytes()))
            );
        }

        // Check filename
        String originalFilename = audioFile.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            throw new IllegalArgumentException("Audio file must have a valid filename");
        }

        // Check file extension
        String extension = getFileExtension(originalFilename).toLowerCase();
        if (!voiceAIConfig.getSupportedFormats().contains(extension)) {
            throw new IllegalArgumentException(
                String.format("Unsupported audio format: %s. Supported formats: %s", 
                        extension, String.join(", ", voiceAIConfig.getSupportedFormats()))
            );
        }

        // Validate MIME type
        String detectedMimeType = tika.detect(audioFile.getInputStream());
        if (!isValidAudioMimeType(detectedMimeType)) {
            throw new IllegalArgumentException(
                String.format("Invalid or unsupported audio file type detected: %s", detectedMimeType)
            );
        }

        log.debug("Audio file validation passed for: {}", originalFilename);
    }

    /**
     * Extract audio metadata using Java Sound API where possible
     */
    public AudioMetadata extractAudioMetadata(File audioFile) throws Exception {
        AudioMetadata.AudioMetadataBuilder builder = AudioMetadata.builder()
                .fileName(audioFile.getName())
                .fileSizeBytes(audioFile.length())
                .format(getFileExtension(audioFile.getName()));

        try {
            // Try to get detailed audio information
            AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(audioFile);
            AudioFormat format = audioInputStream.getFormat();
            
            long frames = audioInputStream.getFrameLength();
            double durationSeconds = frames / format.getFrameRate();
            
            builder.durationSeconds(durationSeconds)
                   .sampleRate((int) format.getSampleRate())
                   .channels(format.getChannels())
                   .bitDepth(format.getSampleSizeInBits());
                   
            audioInputStream.close();
            
        } catch (UnsupportedAudioFileException e) {
            // For formats not supported by Java Sound API, estimate duration
            log.debug("Cannot extract detailed metadata for {}, using estimation", audioFile.getName());
            double estimatedDuration = estimateAudioDuration(audioFile);
            builder.durationSeconds(estimatedDuration);
        }

        return builder.build();
    }

    /**
     * Optimize audio file for OpenAI Whisper processing
     */
    public File optimizeAudioForWhisper(File audioFile, AudioMetadata metadata) throws Exception {
        // If the audio is already in a good format and not too large, return as-is
        if (isOptimalForWhisper(audioFile, metadata)) {
            log.debug("Audio file is already optimal for Whisper processing");
            return audioFile;
        }

        log.info("Optimizing audio file for Whisper processing");

        // Create optimized file name
        String optimizedFileName = "optimized_" + System.currentTimeMillis() + ".wav";
        File optimizedFile = new File(voiceAIConfig.getTempDirectory(), optimizedFileName);

        try {
            // For now, we'll keep the original file as-is since OpenAI Whisper
            // handles most formats well. In a production environment, you might
            // want to add actual audio conversion using FFmpeg or similar
            Files.copy(audioFile.toPath(), optimizedFile.toPath());
            
            log.info("Audio optimization completed: {}", optimizedFile.getName());
            return optimizedFile;
            
        } catch (Exception e) {
            if (optimizedFile.exists()) {
                optimizedFile.delete();
            }
            throw new RuntimeException("Failed to optimize audio file", e);
        }
    }

    /**
     * Create temporary file with unique name
     */
    private File createTempFile(MultipartFile audioFile) throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String extension = getFileExtension(audioFile.getOriginalFilename());
        
        String fileName = String.format("voice_%s_%s.%s", timestamp, uuid, extension);
        File tempFile = new File(voiceAIConfig.getTempDirectory(), fileName);
        
        // Ensure temp directory exists
        tempFile.getParentFile().mkdirs();
        
        // Write audio data to temp file
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(audioFile.getBytes());
        }
        
        log.debug("Created temporary audio file: {}", tempFile.getAbsolutePath());
        return tempFile;
    }

    /**
     * Check if audio file is already optimal for Whisper
     */
    private boolean isOptimalForWhisper(File audioFile, AudioMetadata metadata) {
        // Whisper works well with most formats, so we'll keep it simple
        return audioFile.length() < (10 * 1024 * 1024) && // Less than 10MB
               metadata.getDurationSeconds() < 300; // Less than 5 minutes
    }

    /**
     * Estimate audio duration for unsupported formats
     */
    private double estimateAudioDuration(File audioFile) {
        // Very rough estimation based on file size and typical bitrates
        // This is not accurate but provides a basic check
        long fileSizeBytes = audioFile.length();
        
        // Assume average bitrate of 128 kbps
        double estimatedDurationSeconds = (fileSizeBytes * 8.0) / (128 * 1000);
        
        log.debug("Estimated audio duration: {:.1f} seconds", estimatedDurationSeconds);
        return estimatedDurationSeconds;
    }

    /**
     * Check if MIME type is valid for audio
     */
    private boolean isValidAudioMimeType(String mimeType) {
        return mimeType != null && mimeType.startsWith("audio/");
    }

    /**
     * Get file extension from filename
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    /**
     * Clean up temporary audio files
     */
    public void cleanupTempFile(File... files) {
        if (files != null) {
            for (File file : files) {
                if (file != null && file.exists()) {
                    if (file.delete()) {
                        log.debug("Cleaned up temporary file: {}", file.getName());
                    } else {
                        log.warn("Failed to delete temporary file: {}", file.getAbsolutePath());
                    }
                }
            }
        }
    }

    /**
     * Clean up processed audio file
     */
    public void cleanupProcessedAudioFile(ProcessedAudioFile processedAudio) {
        if (processedAudio != null && processedAudio.getTempFiles() != null) {
            processedAudio.getTempFiles().forEach(file -> {
                if (file != null && file.exists()) {
                    if (file.delete()) {
                        log.debug("Cleaned up processed audio file: {}", file.getName());
                    }
                }
            });
        }
    }

    /**
     * Scheduled cleanup of old temporary files
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    public void scheduledTempFileCleanup() {
        cleanupOldTempFiles();
    }

    /**
     * Clean up old temporary files
     */
    public void cleanupOldTempFiles() {
        try {
            File tempDir = voiceAIConfig.getTempDirectory();
            if (!tempDir.exists()) {
                return;
            }

            File[] files = tempDir.listFiles();
            if (files == null) {
                return;
            }

            long cutoffTime = System.currentTimeMillis() - voiceAIConfig.getCleanupInterval();
            int cleanedCount = 0;

            for (File file : files) {
                if (file.isFile() && file.lastModified() < cutoffTime) {
                    if (file.delete()) {
                        cleanedCount++;
                        log.debug("Cleaned up old temporary file: {}", file.getName());
                    }
                }
            }

            if (cleanedCount > 0) {
                log.info("Cleaned up {} old temporary audio files", cleanedCount);
            }

        } catch (Exception e) {
            log.error("Error during scheduled temporary file cleanup", e);
        }
    }

    /**
     * Get temporary directory statistics
     */
    public Map<String, Object> getTempDirectoryStats() {
        File tempDir = voiceAIConfig.getTempDirectory();
        
        if (!tempDir.exists()) {
            return Map.of(
                "exists", false,
                "fileCount", 0,
                "totalSize", 0L
            );
        }
        
        File[] files = tempDir.listFiles();
        if (files == null) {
            files = new File[0];
        }
        
        long totalSize = Arrays.stream(files)
                .filter(File::isFile)
                .mapToLong(File::length)
                .sum();
        
        return Map.of(
            "exists", true,
            "fileCount", files.length,
            "totalSize", totalSize,
            "totalSizeFormatted", FileUtils.byteCountToDisplaySize(totalSize),
            "path", tempDir.getAbsolutePath()
        );
    }

    /**
     * Audio metadata class
     */
    @lombok.Builder
    @lombok.Data
    public static class AudioMetadata {
        private String fileName;
        private String format;
        private double durationSeconds;
        private long fileSizeBytes;
        private Integer sampleRate;
        private Integer channels;
        private Integer bitDepth;
    }

    /**
     * Processed audio file class
     */
    @lombok.Builder
    @lombok.Data
    public static class ProcessedAudioFile {
        private File originalFile;
        private File processedFile;
        private AudioMetadata metadata;
        private List<File> tempFiles;
    }
}