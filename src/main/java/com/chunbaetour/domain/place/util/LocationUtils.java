package com.chunbaetour.domain.place.util;

import java.math.BigDecimal;
import java.util.Locale;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

public class LocationUtils {

    /**
     * 중심점과 반경을 바탕으로 MBR(Minimum Bounding Rectangle) 폴리곤 문자열 생성
     * MySQL 공간 인덱스(MBRContains)에 사용할 POLYGON 문자열을 반환합니다.
     */
    public static String calculateMbrPolygon(double lat, double lng, double radiusMeters) {
        // MBR 박스 계산 (1도는 약 111km 가정)
        double latDegree = radiusMeters / 111000.0;
        // 경도당 거리는 위도에 따라 달라지므로 cos 적용 (극지방 0 나누기 방어)
        double cosLat = Math.max(0.00001, Math.abs(Math.cos(Math.toRadians(lat))));
        double lngDegree = radiusMeters / (111000.0 * cosLat);

        // DB 마이그레이션이 축-순서 POINT(lng lat) 순서로 적용, WKT 파싱 오류 방지를 위해 Locale.US 명시
        return String.format(Locale.US, "POLYGON((%s %s, %s %s, %s %s, %s %s, %s %s))",
                BigDecimal.valueOf(lng - lngDegree).toPlainString(), BigDecimal.valueOf(lat - latDegree).toPlainString(),
                BigDecimal.valueOf(lng + lngDegree).toPlainString(), BigDecimal.valueOf(lat - latDegree).toPlainString(),
                BigDecimal.valueOf(lng + lngDegree).toPlainString(), BigDecimal.valueOf(lat + latDegree).toPlainString(),
                BigDecimal.valueOf(lng - lngDegree).toPlainString(), BigDecimal.valueOf(lat + latDegree).toPlainString(),
                BigDecimal.valueOf(lng - lngDegree).toPlainString(), BigDecimal.valueOf(lat - latDegree).toPlainString()
        );
    }

    /**
     * Bounding Box 좌표를 바탕으로 MBR 폴리곤 문자열 생성 (PHASE 8-1)
     */
    public static String calculateMbrPolygon(double swLat, double swLng, double neLat, double neLng) {
        return String.format(Locale.US, "POLYGON((%s %s, %s %s, %s %s, %s %s, %s %s))",
                BigDecimal.valueOf(swLng).toPlainString(), BigDecimal.valueOf(swLat).toPlainString(),
                BigDecimal.valueOf(swLng).toPlainString(), BigDecimal.valueOf(neLat).toPlainString(),
                BigDecimal.valueOf(neLng).toPlainString(), BigDecimal.valueOf(neLat).toPlainString(),
                BigDecimal.valueOf(neLng).toPlainString(), BigDecimal.valueOf(swLat).toPlainString(),
                BigDecimal.valueOf(swLng).toPlainString(), BigDecimal.valueOf(swLat).toPlainString()
        );
    }

    /**
     * 위도/경도를 SRID 4326의 JTS Point 객체로 변환합니다.
     */
    public static Point createPoint(BigDecimal lat, BigDecimal lng) {
        if (lat == null || lng == null) return null;
        GeometryFactory gf = new GeometryFactory();
        Point point = gf.createPoint(new Coordinate(lng.doubleValue(), lat.doubleValue()));
        point.setSRID(4326);
        return point;
    }
}
