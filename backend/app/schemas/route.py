from pydantic import BaseModel

class RoutePublic(BaseModel):
    """
    Represents the route information including distance, duration, and polyline.
    """
    distance_meters: float
    duration_seconds: float
    polyline: str