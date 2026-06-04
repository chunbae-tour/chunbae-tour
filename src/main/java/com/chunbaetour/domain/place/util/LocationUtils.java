package com.chunbaetour.domain.place.util;

public class LocationUtils {

    /**
     * 중심점(lat, lng)과 반경(radiusMeters)을 기반으로 대략적인 사각형 박스(MBR) WKT 문자열을 생성합니다.
     * MySQL 공간 인덱스(MBRContains)에 사용할 POLYGON 문자열을 반환합니다.
     */
    public static String calculateMbrPolygon(double lat, double lng, double radiusMeters) {
        // MBR 박스 계산 (1도 당 약 111km 가정)
        double latDegree = radiusMeters / 111000.0;
        // 경도는 위도에 따라 거리가 달라지므로 cos 적용
        double lngDegree = radiusMeters / (111000.0 * Math.cos(Math.toRadians(lat)));

        // DB 마이그레이션과 일치하도록 POINT(lng lat) 순서 사용
        return String.format("POLYGON((%f %f, %f %f, %f %f, %f %f, %f %f))",
                lng - lngDegree, lat - latDegree,
                lng + lngDegree, lat - latDegree,
                lng + lngDegree, lat + latDegree,
                lng - lngDegree, lat + latDegree,
                lng - lngDegree, lat - latDegree);
    }
}
