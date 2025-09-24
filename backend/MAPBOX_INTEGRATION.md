# Mapbox Integration Documentation

This document describes the complete Mapbox integration in the FastAPI backend for the waste collection mobile application.

## Overview

The backend integrates three key Mapbox APIs to provide location-based services:

1. **Geocoding API** - Convert addresses to coordinates when creating orders
2. **Matrix API** - Calculate real travel times and distances for nearby orders
3. **Directions API** - Generate turn-by-turn routes for order pickup

## Configuration

### Environment Variables

```bash
MAPBOX_ACCESS_TOKEN=pk.eyJ1...  # Your Mapbox access token
```

Get your token from: https://account.mapbox.com/access-tokens/

### Required Permissions

Your Mapbox token needs access to:
- Geocoding API
- Directions API  
- Matrix API

## API Endpoints

### 1. Create Order with Geocoding

```http
POST /api/orders/
Content-Type: application/json
Authorization: Bearer <user_token>

{
  "pickup_address": "123 Nguyen Hue Street, District 1, Ho Chi Minh City"
}
```

**What happens:**
1. Backend geocodes the address using Mapbox Geocoding API
2. Stores coordinates in PostGIS geometry column
3. Returns order with location data

**Response:**
```json
{
  "id": "uuid",
  "pickup_address": "123 Nguyen Hue Street...",
  "location": {
    "type": "Point",
    "coordinates": [106.7008, 10.7756]
  },
  "status": "pending",
  ...
}
```

### 2. Find Nearby Orders with Travel Info

```http
GET /api/orders/nearby?lat=10.7756&lng=106.7008&radius_km=10&limit=20
Authorization: Bearer <collector_token>
```

**What happens:**
1. PostGIS finds orders within radius using spatial query
2. Mapbox Matrix API calculates real travel times/distances
3. Returns enriched order data sorted by proximity

**Response:**
```json
[
  {
    "id": "uuid",
    "pickup_address": "456 Le Loi Street...",
    "distance_km": 2.5,
    "travel_time_seconds": 480,
    "travel_distance_meters": 2847,
    "location": {...},
    ...
  }
]
```

### 3. Get Route to Order

```http
GET /api/orders/{order_id}/route?lat=10.7756&lng=106.7008
Authorization: Bearer <collector_token>
```

**What happens:**
1. Mapbox Directions API generates optimized route
2. Returns polyline, distance, and estimated duration

**Response:**
```json
{
  "distance_meters": 2847,
  "duration_seconds": 480,
  "polyline": "s`l~Fqxr}O@FJ@{AoBaB..."
}
```

## Service Functions

All Mapbox functionality is centralized in `app/services/mapbox.py`:

### Geocoding
```python
from app.services import mapbox

# Convert address to coordinates
result = await mapbox.geocode_address("Ho Chi Minh City, Vietnam")
# Returns: {"lng": 106.6297, "lat": 10.8231}
```

### Travel Information
```python
# Get travel time/distance from one point to multiple destinations
origin = (106.6297, 10.8231)  # (lng, lat)
destinations = [(106.6508, 10.7769), (106.6920, 10.7629)]

results = await mapbox.get_travel_info_from_mapbox(origin, destinations)
# Returns: [{"duration": 480, "distance": 2847}, ...]
```

### Route Directions
```python
# Get route from point A to point B
route = await mapbox.get_route_from_mapbox(
    start_lon=106.6297, start_lat=10.8231,
    end_lon=106.6508, end_lat=10.7769
)
# Returns: {"distance_meters": 2847, "duration_seconds": 480, "polyline": "..."}
```

## Error Handling

All Mapbox service functions include comprehensive error handling:

- **Network errors**: Graceful fallback with proper HTTP status codes
- **API errors**: Mapbox API error messages passed through
- **Invalid input**: Validation errors with clear messages
- **Rate limits**: Proper HTTP 429 handling

Example error responses:
```json
{
  "detail": "Could not find coordinates for address: Invalid Address"
}
```

## Database Integration

### PostGIS Geometry Storage

Orders store location as PostGIS POINT geometry:
```sql
ALTER TABLE order ADD COLUMN location geometry(POINT, 4326);
CREATE INDEX idx_order_location ON order USING GIST (location);
```

### Spatial Queries

The backend uses PostGIS for efficient spatial operations:
```python
# Find orders within radius
query = db.query(Order, distance_expression).filter(
    func.ST_DWithin(Order.location, collector_location_wkt, radius_meters),
    Order.status == OrderStatus.PENDING
).order_by(distance_expression).limit(limit)
```

## Mobile App Integration

### Android (Kotlin)
```kotlin
data class NearbyOrderPublic(
    @SerializedName("id") val id: String,
    @SerializedName("distance_km") val distanceKm: Double,
    @SerializedName("travel_time_seconds") val travelTimeSeconds: Double?,
    @SerializedName("travel_distance_meters") val travelDistanceMeters: Double?,
    // ... other fields
)
```

### Route Polyline Decoding
The polyline returned by the API is encoded using Mapbox's polyline6 format. Use Mapbox SDK to decode:

```kotlin
// Decode polyline for map display
val decodedRoute = PolylineUtils.decode(routeResponse.polyline, 6)
```

## Performance Considerations

### Caching
- Consider caching geocoding results for common addresses
- Matrix API results can be cached for short periods
- Route calculations should be real-time for traffic data

### Rate Limits
- Mapbox APIs have rate limits based on your plan
- Implement exponential backoff for failed requests
- Monitor usage in Mapbox dashboard

### Optimization
- Batch multiple destinations in Matrix API calls
- Use appropriate routing profiles (driving-traffic for real-time)
- Limit nearby search radius and results for performance

## Testing

Use the included validation scripts:

```bash
# Test API structure
python /tmp/mapbox_tests/test_api_structure.py

# Comprehensive backend validation  
python /tmp/mapbox_tests/validate_backend.py

# Mapbox service testing (requires valid token)
MAPBOX_ACCESS_TOKEN=your_token python /tmp/mapbox_tests/test_mapbox_service.py
```

## Deployment

### Environment Setup
1. Set `MAPBOX_ACCESS_TOKEN` in production environment
2. Ensure PostGIS is enabled in PostgreSQL
3. Run database migrations: `alembic upgrade head`

### Monitoring
- Monitor Mapbox API usage in dashboard
- Set up alerts for API errors and rate limits
- Track response times for location-based endpoints

## Security

- Mapbox token should be server-side only (never expose in mobile app)
- Use environment variables for all secrets
- Consider IP restrictions on Mapbox token if needed
- Validate all coordinate inputs to prevent injection

## Troubleshooting

### Common Issues

1. **"Could not find coordinates"**
   - Check address format and completeness
   - Verify Mapbox token has geocoding permissions

2. **"Matrix API error"**
   - Ensure coordinates are valid (lat: -90 to 90, lng: -180 to 180)  
   - Check if destinations array is not empty

3. **"Route generation failed"**
   - Verify start and end coordinates are accessible by road
   - Check routing profile supports the location

### Debug Mode
Set logging level to DEBUG to see Mapbox API request/response details:
```python
import logging
logging.getLogger("httpx").setLevel(logging.DEBUG)
```

---

## Summary

The backend now provides a complete location-based service infrastructure:
- ✅ Address geocoding for order creation
- ✅ Real-time travel calculations for nearby orders  
- ✅ Turn-by-turn routing for order pickup
- ✅ Efficient spatial queries with PostGIS
- ✅ Mobile-friendly API responses
- ✅ Comprehensive error handling
- ✅ Production-ready architecture

The implementation is ready for production use with proper monitoring and scaling considerations.