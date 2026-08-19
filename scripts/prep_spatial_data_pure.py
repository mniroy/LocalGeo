import json
import sqlite3
import struct
import math
import sys
import os

def perpendicular_distance(pt, line_start, line_end):
    px, py = pt[0], pt[1]
    x1, y1 = line_start[0], line_start[1]
    x2, y2 = line_end[0], line_end[1]

    dx = x2 - x1
    dy = y2 - y1

    if dx == 0 and dy == 0:
        return math.hypot(px - x1, py - y1)

    # Calculate the t that minimizes distance
    t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)

    # Clamp t to segment range
    t = max(0.0, min(1.0, t))

    proj_x = x1 + t * dx
    proj_y = y1 + t * dy

    return math.hypot(px - proj_x, py - proj_y)

def ramer_douglas_peucker(points, epsilon):
    if len(points) <= 2:
        return points

    dmax = 0.0
    index = 0
    end = len(points) - 1

    for i in range(1, end):
        d = perpendicular_distance(points[i], points[0], points[end])
        if d > dmax:
            index = i
            dmax = d

    if dmax > epsilon:
        rec_results1 = ramer_douglas_peucker(points[:index+1], epsilon)
        rec_results2 = ramer_douglas_peucker(points[index:], epsilon)
        return rec_results1[:-1] + rec_results2
    else:
        return [points[0], points[end]]

def simplify_ring(coords, epsilon=0.0001):
    if epsilon <= 0 or len(coords) <= 4:
        return coords
    simplified = ramer_douglas_peucker(coords, epsilon)
    if len(simplified) < 4:
        return coords
    if simplified[0] != simplified[-1]:
        simplified.append(simplified[0])
    return simplified

def encode_geojson_geometry(geom, epsilon=0.0001):
    gtype = geom.get('type', '')
    coords = geom.get('coordinates', [])

    polys = []
    if gtype == 'Polygon':
        polys = [coords]
    elif gtype == 'MultiPolygon':
        polys = coords
    else:
        return None, 0, 0, 0, 0, 0

    min_lon, max_lon = float('inf'), float('-inf')
    min_lat, max_lat = float('inf'), float('-inf')

    # Compute exact BBOX from original geometry points
    for poly in polys:
        for ring in poly:
            for pt in ring:
                lon, lat = pt[0], pt[1]
                if lon < min_lon: min_lon = lon
                if lon > max_lon: max_lon = lon
                if lat < min_lat: min_lat = lat
                if lat > max_lat: max_lat = lat

    encoded_polys = []
    for poly in polys:
        encoded_rings = []
        for ring in poly:
            simplified = simplify_ring(ring, epsilon)
            encoded_rings.append(simplified)
        encoded_polys.append(encoded_rings)

    # Encode binary blob
    buf = bytearray()
    buf.extend(struct.pack('<I', len(encoded_polys)))

    for poly in encoded_polys:
        buf.extend(struct.pack('<I', len(poly)))
        for ring in poly:
            buf.extend(struct.pack('<I', len(ring)))
            for pt in ring:
                buf.extend(struct.pack('<dd', float(pt[0]), float(pt[1])))

    return bytes(buf), min_lon, max_lon, min_lat, max_lat, len(encoded_polys)

def main():
    json_path = 'gadm41_IDN_3.json'
    db_path = 'indonesia_kecamatan.db'

    if os.path.exists(db_path):
        os.remove(db_path)

    print(f"Loading {json_path}...")
    with open(json_path, 'r', encoding='utf-8') as f:
        data = json.load(f)

    features = data.get('features', [])
    print(f"Loaded {len(features)} sub-district features.")

    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()

    cursor.execute("""
        CREATE TABLE sub_districts (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name_kecamatan TEXT NOT NULL,
            name_kabupaten TEXT NOT NULL,
            name_provinsi TEXT NOT NULL,
            min_lon REAL NOT NULL,
            max_lon REAL NOT NULL,
            min_lat REAL NOT NULL,
            max_lat REAL NOT NULL,
            polygon_blob BLOB NOT NULL
        );
    """)

    cursor.execute("""
        CREATE VIRTUAL TABLE idx_subdistricts_bbox USING rtree(
            id,
            min_lon, max_lon,
            min_lat, max_lat
        );
    """)

    count = 0
    for feat in features:
        props = feat.get('properties', {})
        name_kecamatan = props.get('NAME_3', 'Unknown') or 'Unknown'
        name_kabupaten = props.get('NAME_2', 'Unknown') or 'Unknown'
        name_provinsi = props.get('NAME_1', 'Unknown') or 'Unknown'

        geom = feat.get('geometry')
        if not geom:
            continue

        blob, min_lon, max_lon, min_lat, max_lat, num_polys = encode_geojson_geometry(geom, epsilon=0.0001)
        if blob is None or num_polys == 0:
            continue

        cursor.execute("""
            INSERT INTO sub_districts (name_kecamatan, name_kabupaten, name_provinsi, min_lon, max_lon, min_lat, max_lat, polygon_blob)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?);
        """, (name_kecamatan, name_kabupaten, name_provinsi, min_lon, max_lon, min_lat, max_lat, blob))

        row_id = cursor.lastrowid

        cursor.execute("""
            INSERT INTO idx_subdistricts_bbox (id, min_lon, max_lon, min_lat, max_lat)
            VALUES (?, ?, ?, ?, ?);
        """, (row_id, min_lon, max_lon, min_lat, max_lat))

        count += 1
        if count % 1000 == 0:
            print(f"Processed {count}/{len(features)} sub-districts...")

    conn.commit()
    conn.close()

    db_size_mb = os.path.getsize(db_path) / (1024 * 1024)
    print(f"Successfully created {db_path} with {count} records.")
    print(f"Database file size: {db_size_mb:.2f} MB")

if __name__ == '__main__':
    main()
