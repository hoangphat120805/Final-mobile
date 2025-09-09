import cloudinary
import cloudinary.uploader
import cloudinary.api

from app.core.config import settings
from fastapi import UploadFile

cloudinary.config(
    cloud_name = settings.CLOUD_NAME,
    api_key = settings.CLOUD_API_KEY,
    api_secret = settings.CLOUD_API_SECRET
)

def upload_avatar(file: UploadFile, public_id: str) -> str:
    try:
        result = cloudinary.uploader.upload(file.file, folder="vechai_user_avatars", public_id=public_id, overwrite=True, resource_type="image")
        return result.get("secure_url")
    except Exception as e:
        print(f"Error uploading image to Cloudinary: {e}")
        return ""

def upload_category_icon(file: UploadFile, public_id: str) -> str:
    try:
        result = cloudinary.uploader.upload(file.file, folder="vechai_category_icons", public_id=public_id, overwrite=True, resource_type="image")
        return result.get("secure_url")
    except Exception as e:
        print(f"Error uploading image to Cloudinary: {e}")
        return ""

def upload_order_image(file1: UploadFile, file2: UploadFile, public_id: str) -> list[str]:
    try:
        result1 = cloudinary.uploader.upload(file1.file, folder="vechai_order_images", public_id=public_id+"_1", overwrite=True, resource_type="image")
        result2 = cloudinary.uploader.upload(file2.file, folder="vechai_order_images", public_id=public_id+"_2", overwrite=True, resource_type="image")
        return [result1.get("secure_url"), result2.get("secure_url")]
    except Exception as e:
        print(f"Error uploading image to Cloudinary: {e}")
        return []
    
def upload_static_image(file: UploadFile, public_id: str) -> str:
    try:
        result = cloudinary.uploader.upload(file.file, folder="vechai_static_images", public_id=public_id, overwrite=True, resource_type="image")
        return result.get("secure_url")
    except Exception as e:
        print(f"Error uploading image to Cloudinary: {e}")
        return ""