package com.chunbaetour.infrastructure.flyway;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class FlywayMigrationPatcher {

    /**
     * 빈 DB(MySQL 8.4) 테스트 컨테이너 환경에서 ALGORITHM=INSTANT 구문 오류 우회.
     * 원본 build/ 폴더를 오염시키지 않기 위해, 임시 디렉토리를 생성하여 마이그레이션 파일들을 복사한 뒤,
     * ALGORITHM=INSTANT 구문만 제거하고 임시 경로(filesystem:...)를 반환합니다.
     */
    public static String setupTempMigrations() {
        try {
            Path sourceDir = Paths.get("build/resources/main/db/migration");
            if (!Files.exists(sourceDir)) {
                throw new RuntimeException("Migration 빌드 폴더가 존재하지 않습니다: " + sourceDir.toAbsolutePath() + 
                                           ". 테스트 실행 전 Gradle build(또는 processResources)가 선행되어야 합니다.");
            }

            Path tempDir = Files.createTempDirectory("flyway-test-shim-");
            tempDir.toFile().deleteOnExit();

            try (Stream<Path> paths = Files.walk(sourceDir)) {
                paths.filter(Files::isRegularFile)
                     .filter(p -> p.toString().endsWith(".sql"))
                     .forEach(p -> {
                         try {
                             String content = Files.readString(p);
                             
                             // V2220의 주석에 포함된 설명문구가 불필요하게 변경되는 것을 막음
                             if (!p.getFileName().toString().contains("V202606042220")) {
                                 if (content.contains("ALGORITHM = INSTANT")) {
                                     content = content.replaceAll("(?i),\\s*ALGORITHM\\s*=\\s*INSTANT", "");
                                 }
                             }
                             
                             Path destFile = tempDir.resolve(sourceDir.relativize(p));
                             Files.createDirectories(destFile.getParent());
                             Files.writeString(destFile, content);
                             destFile.toFile().deleteOnExit();
                         } catch (IOException e) {
                             throw new RuntimeException("파일 치환 및 복사 실패: " + p.getFileName(), e);
                         }
                     });
            }
            return "filesystem:" + tempDir.toAbsolutePath().toString().replace("\\", "/");
        } catch (IOException e) {
            throw new RuntimeException("임시 디렉토리 생성 실패", e);
        }
    }
}
