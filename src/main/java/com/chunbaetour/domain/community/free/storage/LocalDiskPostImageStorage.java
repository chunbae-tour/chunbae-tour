package com.chunbaetour.domain.community.free.storage;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 로컬/테스트용 자유게시판 이미지 저장소 (KAN-317, {@code @Profile("!prod")}).
 *
 * <p>운영(S3) 외 환경에서 업로드 흐름을 실제로 확인할 수 있도록 <b>로컬 디스크에 저장</b>한다(throw 안 함).
 * S3와 동일한 키 형식({@code posts/free/{userId}/{uuid}.{ext}})을 반환해 후속 조회/연동 코드가 환경 무관하게 동작한다.
 * 저장 위치는 {@code post.image.local-dir}(미설정 시 {@code java.io.tmpdir}/chunbae-uploads).
 */
@Slf4j
@Component
@Profile("!prod")
public class LocalDiskPostImageStorage implements PostImageStorage {

    private final Path baseDir;

    public LocalDiskPostImageStorage(@Value("${post.image.local-dir:}") String configuredDir) {
        String dir = (configuredDir == null || configuredDir.isBlank())
                ? System.getProperty("java.io.tmpdir") + "/chunbae-uploads"
                : configuredDir;
        this.baseDir = Path.of(dir);
    }

    @Override
    public String upload(Long userId, MultipartFile file) {
        String key = PostImageKeys.objectKey(userId, file.getContentType());
        Path target = baseDir.resolve(key);
        try {
            Files.createDirectories(target.getParent());
            try (var in = file.getInputStream()) {
                Files.copy(in, target);
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
        return key;
    }

    /**
     * 로컬은 실제 presign이 없으므로 키를 그대로 반환(passthrough). 개발자는 키로 로컬 파일을 직접 확인.
     * 운영(S3)에서만 만료 있는 presigned URL이 발급된다.
     */
    @Override
    public String presignedGetUrl(String key) {
        return key;
    }

    /** 로컬 디스크 파일 삭제(KAN-317). best-effort — 없거나 실패해도 throw 안 함(고아는 스케줄러 회수 대상). */
    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(baseDir.resolve(key));
        } catch (IOException e) {
            log.warn("로컬 자유게시판 이미지 삭제 실패(잔존, 스케줄러 회수 대상): key={}", key, e);
        }
    }

    /**
     * prefix 아래 로컬 파일을 walk로 나열한다(KAN-317). 키는 baseDir 기준 상대경로를 '/'로 정규화(S3 키 형식 일치),
     * lastModified는 파일 mtime을 Instant로. baseDir/prefix 미존재 시 빈 목록.
     */
    @Override
    public List<PostImageObjectInfo> listObjects(String prefix) {
        Path root = baseDir.resolve(prefix);
        if (!Files.exists(root)) {
            return List.of();
        }
        List<PostImageObjectInfo> result = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile).forEach(p -> {
                try {
                    String key = baseDir.relativize(p).toString().replace('\\', '/');
                    Instant lastModified = Files.getLastModifiedTime(p).toInstant();
                    result.add(new PostImageObjectInfo(key, lastModified));
                } catch (IOException e) {
                    log.warn("로컬 객체 메타 읽기 실패, 건너뜀: path={}", p, e);
                }
            });
        } catch (IOException e) {
            log.warn("로컬 객체 나열 실패: prefix={}", prefix, e);
            return List.of();
        }
        return result;
    }
}
