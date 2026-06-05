package com.chunbaetour.infrastructure.flyway;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class FlywayMigrationPatcher {

    /**
     * MySQL 8.4 테스트 컨테이너 환경에서 ALGORITHM=INSTANT 구문 오류 우회.
     * 빌드된 SQL 파일들에서 ALGORITHM=INSTANT 구문을 정규식으로 치환하여 제거합니다.
     */
    public static void removeAlgorithmInstant() {
        try {
            Path dirPath = Paths.get("build/resources/main/db/migration");
            
            if (!Files.exists(dirPath)) {
                throw new RuntimeException("Migration 빌드 폴더가 존재하지 않습니다: " + dirPath.toAbsolutePath() + 
                                           ". 테스트를 실행하기 전 Gradle build(또는 processResources)가 선행되어야 합니다.");
            }
            
            try (Stream<Path> paths = Files.walk(dirPath)) {
                paths.filter(Files::isRegularFile)
                     .filter(p -> p.toString().endsWith(".sql"))
                     .forEach(p -> {
                         try {
                             String content = Files.readString(p);
                             if (content.contains("ALGORITHM = INSTANT")) {
                                 content = content.replaceAll(",\\s*ALGORITHM\\s*=\\s*INSTANT", "");
                                 Files.writeString(p, content);
                             }
                         } catch (Exception e) {
                             throw new RuntimeException("파일 읽기/쓰기 실패: " + p.getFileName(), e);
                         }
                     });
            }
        } catch (Exception e) {
            throw new RuntimeException("ALGORITHM=INSTANT 치환 실패", e);
        }
    }
}
