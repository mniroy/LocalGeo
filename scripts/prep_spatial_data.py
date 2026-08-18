import json
import sqlite3
import struct
import sys
import os

import shapely.geometry
import geopandas as gpd

def encode_geometry(geom):
    """
    Encode Shapely Polygon or MultiPolygon into binary format for fast Kotlin PIP parsing:
    Int32: num_polygons
    For each polygon:
        Int32: num_rings (1 exterior ring + N interior holes)
        For each ring:
            Int32: num_points
            Array of (Float64 lon, Float64 lat) -> num_points * 16 bytes
    """
    if geom.geom_type == 'Polygon':
        polys = [geom]
    elif geom.geom_type == 'MultiPolygon':
        polys = list(geom.geoms)
    else:
        polys = []

    buf = bytearray()
    buf.extend(struct.pack('<I', len(polys)))

    for poly in polys:
        rings = [poly.exterior] + list(poly.interiors)
        buf.extend(struct.pack('<I', len(rings)))
        for ring in rings:
            coords = list(ring.coords)
            buf.extend(struct.pack('<I', len(coords)))
            for lon, lat in coords:
                buf.extend(struct.pack('<dd', float(lon), float(lat)))

    return bytes(buf)

def main():
    json_path = 'gadm41_IDN_3.json'
    db_path = 'indonesia_kecamatan.db'
    
    if os.path.exists(db_path):
        os.remove(db_path)
        
    print(f"Loading {json_path} with geopandas...")
    gdf = gpd.read_file(json_path)
    print(f"Loaded {len(gdf)} sub-district features.")

    # Column mapping
    # NAME_3 -> Sub-District (name_kecamatan)
    # NAME_2 -> Regency/City (name_kabupaten)
    # NAME_1 -> Province (name_provinsi)
    gdf['name_kecamatan'] = gdf['NAME_3'].fillna('Unknown')
    gdf['name_kabupaten'] = gdf['NAME_2'].fillna('Unknown')
    gdf['name_provinsi'] = gdf['NAME_1'].fillna('Unknown')

    # Step 2: Simplify geometry (Douglas-Peucker)
    # Target < 20MB DB size. Tolerance ~ 0.003 to 0.005 degrees (~300-500m precision)
    print("Simplifying geometries with tolerance=0.003...")
    gdf['geometry'] = gdf['geometry'].simplify(tolerance=0.003, preserve_topology=True)

    print("Creating SQLite database with R-Tree spatial index...")
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
    for idx, row in gdf.iterrows():
        geom = row['geometry']
        if geom is None or geom.is_empty:
            continue
            
        bounds = geom.bounds # (minx, miny, maxx, maxy) -> (min_lon, min_lat, max_lon, max_lat)
        min_lon, min_lat, max_lon, max_lat = bounds[0], bounds[1], bounds[2], bounds[3]
        
        blob = encode_geometry(geom)
        
        cursor.execute("""
            INSERT INTO sub_districts (name_kecamatan, name_kabupaten, name_provinsi, min_lon, max_lon, min_lat, max_lat, polygon_blob)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?);
        """, (row['name_kecamatan'], row['name_kabupaten'], row['name_provinsi'], min_lon, max_lon, min_lat, max_lat, blob))
        
        row_id = cursor.lastrowid
        
        cursor.execute("""
            INSERT INTO idx_subdistricts_bbox (id, min_lon, max_lon, min_lat, max_lat)
            VALUES (?, ?, ?, ?, ?);
        """, (row_id, min_lon, max_lon, min_lat, max_lat))
        
        count += 1

    conn.commit()
    conn.close()

    db_size_mb = os.path.getsize(db_path) / (1024 * 1024)
    print(f"Successfully created {db_path} with {count} records.")
    print(f"Database file size: {db_size_mb:.2f} MB")

if __name__ == '__main__':
    main()
