import httpx
from app.core.config import settings
from typing import List, Tuple, Dict, Optional

async def get_travel_info_from_mapbox(
    origin: Tuple[float, float], # (lng, lat)
    destinations: List[Tuple[float, float]] # Danh sách (lng, lat)
) -> List[Dict[str, float]] | None:
    """
   USE MAPBOX MATRIX API to get travel duration and distance from origin to multiple destinations.
    """
    if not destinations:
        return []

    # Định dạng tọa độ: lon,lat;lon,lat;...
    coords_str = f"{origin[0]},{origin[1]};" + ";".join([f"{lon},{lat}" for lon, lat in destinations])
    
    profile = "mapbox/driving-traffic" # driving, walking, cycling

    request_url = f"https://api.mapbox.com/directions-matrix/v1/{profile}/{coords_str}"
    params = {
        "sources": "0", # Điểm xuất phát là index 0
        "destinations": ";".join([str(i+1) for i in range(len(destinations))]), # Các điểm đến
        "annotations": "duration,distance",
        "access_token": settings.MAPBOX_ACCESS_TOKEN
    }

    async with httpx.AsyncClient() as client:
        try:
            response = await client.get(request_url, params=params, timeout=10.0)
            response.raise_for_status()
            data = response.json()

            if data.get("code") == "Ok":
                # Trả về một danh sách các dict {'duration': seconds, 'distance': meters}
                results = []
                for i in range(len(destinations)):
                    results.append({
                        "duration": data["durations"][0][i],
                        "distance": data["distances"][0][i]
                    })
                return results
            return None
        except Exception as e:
            print(f"Error calling Mapbox Matrix API: {e}")
            return None
        

async def get_route_from_mapbox(start_lon: float, start_lat: float, end_lon: float, end_lat: float):
    """
    USE MAPBOX DIRECTIONS API to get route info from start to end point.
    """
    
    # Định dạng tọa độ theo yêu cầu của Mapbox: {lon},{lat}
    coordinates = f"{start_lon},{start_lat};{end_lon},{end_lat}"
    
    # Profile tìm đường: driving-traffic, driving, walking, cycling
    profile = "driving-traffic" # <-- driving-traffic để có dữ liệu giao thông thời gian thực

    # Xây dựng URL request
    request_url = (
        f"https://api.mapbox.com/directions/v5/mapbox/{profile}/{coordinates}"
    )

    params = {
        "alternatives": "false",
        "geometries": "polyline6", # polyline6 là định dạng nén hiệu quả hơn
        "overview": "full",
        "access_token": settings.MAPBOX_ACCESS_TOKEN
    }

    async with httpx.AsyncClient() as client:
        try:
            response = await client.get(request_url, params=params, timeout=10.0)
            response.raise_for_status() # Ném lỗi nếu status code là 4xx hoặc 5xx
            
            data = response.json()
            if data.get("code") == "Ok" and data.get("routes"):
                route = data["routes"][0]
                return {
                    "distance_meters": route.get("distance"),
                    "duration_seconds": route.get("duration"),
                    "polyline": route.get("geometry") # polyline đã được mã hóa
                }
            else:
                print(f"Fail from Mapbox API: {data.get('message')}")
                return None
        except httpx.HTTPStatusError as e:
            print(f"Fail HTTP khi gọi Mapbox: {e.response.text}")
            return None
        except Exception as e:
            print(f"Undefined error when calling Mapbox: {e}")
            return None


async def geocode_address(address: str) -> Optional[Dict[str, float]]:
    """
    USE MAPBOX GEOCODING API to convert address string to (lng, lat).
    """
    # Xây dựng URL. Mapbox yêu cầu địa chỉ phải được URL-encoded.
    # httpx sẽ tự động làm việc này khi truyền qua `params`.
    request_url = f"https://api.mapbox.com/geocoding/v5/mapbox.places/{address}.json"
    
    params = {
        "access_token": settings.MAPBOX_ACCESS_TOKEN,
        "limit": 1 # Chỉ lấy kết quả phù hợp nhất
    }

    async with httpx.AsyncClient() as client:
        try:
            response = await client.get(request_url, params=params, timeout=10.0)
            response.raise_for_status() # Ném lỗi nếu status code là 4xx hoặc 5xx
            
            data = response.json()
            
            # Kiểm tra xem có kết quả không
            if data.get("features"):
                first_result = data["features"][0]
                coordinates = first_result.get("geometry", {}).get("coordinates")
                
                if coordinates and len(coordinates) == 2:
                    # Mapbox trả về [longitude, latitude]
                    lng, lat = coordinates
                    return {"lng": lng, "lat": lat}
            
            # Trả về None nếu không tìm thấy
            return None
            
        except httpx.HTTPStatusError as e:
            print(f"HTTP error from request Mapbox Geocoding: {e.response.text}")
            return None
        except Exception as e:
            print(f"undefined error: {e}")
            return None