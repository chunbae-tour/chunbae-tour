package com.chunbaetour.domain.place.util;

import java.math.BigDecimal;
import java.util.Locale;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

public class LocationUtils {

    /**
     * 중심점(lat, lng)과 반경(radiusMeters)을 기반으로 대략적인 사각형 박스(MBR) WKT 문자열을 생성합니다.
     * MySQL 공간 인덱스(MBRContains)에 사용할 POLYGON 문자열을 반환합니다.
     */
    public static String calculateMbrPolygon(double lat, double lng, double radiusMeters) {
        // MBR 박스 계산 (1도 당 약 111km 가정)
        double latDegree = radiusMeters / 111000.0;
        // 경도는 위도에 따라 거리가 달라지므로 cos 적용 (극지방 0 나누기 방어)
        double cosLat = Math.max(0.00001, Math.abs(Math.cos(Math.toRadians(lat))));
        double lngDegree = radiusMeters / (111000.0 * cosLat);

        // DB 마이그레이션과 일치하도록 POINT(lng lat) 순서 사용, WKT 파싱 오류 방지를 위해 Locale.US 명시
        return String.format(Locale.US, "POLYGON((%f %f, %f %f, %f %f, %f %f, %f %f))",
                lng - lngDegree, lat - latDegree,
                lng + lngDegree, lat - latDegree,
                lng + lngDegree, lat + latDegree,
                lng - lngDegree, lat + latDegree,
                lng - lngDegree, lat - latDegree);
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
