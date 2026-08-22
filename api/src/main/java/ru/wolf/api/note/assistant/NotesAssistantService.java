package ru.wolf.api.note.assistant;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class NotesAssistantService {

    private final NotesAssistant assistant;
    private final NotesAssistantProperties properties;

    public NotesAssistantService(NotesAssistant assistant, NotesAssistantProperties properties) {
        this.assistant = assistant;
        this.properties = properties;
    }

    public StoredAudio store(MultipartFile file) {
        String safeName = UUID.randomUUID() + "-" + sanitizeFilename(file.getOriginalFilename());
        Path directory = Paths.get(properties.getAudioDirectory());
        try {
            Files.createDirectories(directory);
            Path target = directory.resolve(safeName).normalize();
            if (!target.getParent().equals(directory.toAbsolutePath().normalize())) {
                throw new IllegalArgumentException("Недопустимое имя аудиофайла");
            }
            file.transferTo(target);
            return new StoredAudio(target.toString(), file.getContentType(), file.getOriginalFilename());
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось сохранить аудиофайл", e);
        }
    }

    public String transcribe(String audioRef) {
        return assistant.transcribe(audioRef);
    }

    public String summarize(Long projectId, List<Long> noteIds) {
        return assistant.summarize(projectId, noteIds);
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "audio.bin";
        }
        return Path.of(filename).getFileName().toString().replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public record StoredAudio(String audioRef, String contentType, String originalFilename) {
    }
}
