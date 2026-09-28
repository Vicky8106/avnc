#!/usr/bin/env python3
"""
Google Play Store Publisher for VNC Android Free (AVNC)
Uses Google Play Developer API (v3) to upload AAB bundles, configure release tracks,
and synchronize localized store listings.
"""

import argparse
import json
import os
import sys
from pathlib import Path
from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload
from googleapiclient.errors import HttpError

DEFAULT_KEY = "/root/play-publisher-key.json"
DEFAULT_PACKAGE = "com.vncandroid.free"
DEFAULT_METADATA = "/root/avnc/metadata"


def get_service(key_path: str):
    if not os.path.exists(key_path):
        raise FileNotFoundError(f"Service account key not found at {key_path}")
    credentials = service_account.Credentials.from_service_account_file(
        key_path,
        scopes=["https://www.googleapis.com/auth/androidpublisher"]
    )
    return build("androidpublisher", "v3", credentials=credentials)


def check_app_status(service, package_name: str):
    print(f"[*] Checking Play Console status for package: {package_name}...")
    try:
        edit = service.edits().insert(packageName=package_name, body={}).execute()
        edit_id = edit["id"]
        print(f"[✓] App found in Play Console! Created active edit ID: {edit_id}")

        tracks = service.edits().tracks().list(packageName=package_name, editId=edit_id).execute()
        print("\n--- Current Tracks & Releases ---")
        for t in tracks.get("tracks", []):
            releases = t.get("releases", [])
            print(f"Track '{t['track']}': {len(releases)} release(s)")
            for r in releases:
                print(f"  - Version Code(s): {r.get('versionCodes', [])} | Status: {r.get('status')} | Name: {r.get('name', 'N/A')}")

        bundles = service.edits().bundles().list(packageName=package_name, editId=edit_id).execute()
        print(f"\nTotal App Bundles (.aab) uploaded: {len(bundles.get('bundles', []))}")

        service.edits().delete(packageName=package_name, editId=edit_id).execute()
        print("[✓] Edit closed cleanly.")
        return True
    except HttpError as e:
        if e.resp.status == 404:
            print(f"\n[!] 404 ERROR: Package '{package_name}' was not found in your Google Play Console.")
            print("\n" + "="*70)
            print("HOW TO RESOLVE (Google Play API Constraint):")
            print("1. The Google Play Developer API cannot create brand-new apps from scratch.")
            print("2. Open Google Play Console: https://play.google.com/console")
            print("3. Click 'Create app'")
            print(f"   - App name: 'VNC android free'")
            print("   - Default language: English (United States)")
            print("   - App or game: App")
            print("   - Free or paid: Free")
            print("   - Accept Developer Program Policies and US export laws")
            print("   - Click 'Create app' at the bottom right.")
            print("4. In 'Users and permissions', verify that service account:")
            print("   'play-publisher@upheld-coast-443818-s1.iam.gserviceaccount.com'")
            print("   has access to this app (or Admin account permissions).")
            print("="*70 + "\n")
        elif e.resp.status == 403:
            print(f"[!] 403 PERMISSION DENIED: Service account lacks permission for '{package_name}'.")
            print("Please check Users & permissions in Google Play Console.")
        else:
            print(f"[!] Error checking app status: {e}")
        return False


def upload_metadata(service, package_name: str, edit_id: str, metadata_dir: str):
    if not os.path.exists(metadata_dir):
        print(f"[!] Metadata directory {metadata_dir} not found. Skipping metadata upload.")
        return

    langs = [d for d in os.listdir(metadata_dir) if os.path.isdir(os.path.join(metadata_dir, d))]
    print(f"[*] Uploading localized store metadata across {len(langs)} languages...")

    default_title_file = os.path.join(metadata_dir, "en-US", "title.txt")
    fallback_title = open(default_title_file).read().strip() if os.path.exists(default_title_file) else "VNC android free"

    for lang in langs:
        p = os.path.join(metadata_dir, lang)
        title_f = os.path.join(p, "title.txt")
        short_f = os.path.join(p, "short_description.txt")
        full_f = os.path.join(p, "full_description.txt")

        title = open(title_f).read().strip() if os.path.exists(title_f) else fallback_title
        short_desc = open(short_f).read().strip() if os.path.exists(short_f) else ""
        full_desc = open(full_f).read().strip() if os.path.exists(full_f) else ""

        if not short_desc and not full_desc:
            continue

        body = {
            "title": title[:30],
            "shortDescription": short_desc[:80],
            "fullDescription": full_desc[:4000]
        }

        try:
            service.edits().listings().update(
                packageName=package_name,
                editId=edit_id,
                language=lang,
                body=body
            ).execute()
            print(f"  [✓] Updated listing for '{lang}'")
        except Exception as e:
            # Some language codes may differ between fastlane and Play Store (e.g. pt-BR vs pt-rBR)
            print(f"  [-] Note for '{lang}': {e}")


def publish_release(service, package_name: str, bundle_path: str, track: str, status: str, metadata_dir: str, validate_only: bool):
    print(f"[*] Starting publication workflow for {package_name}...")
    edit = service.edits().insert(packageName=package_name, body={}).execute()
    edit_id = edit["id"]
    print(f"[✓] Created Play Console edit ID: {edit_id}")

    try:
        version_code = None
        if bundle_path:
            bundle_p = Path(bundle_path)
            if not bundle_p.exists():
                raise FileNotFoundError(f"Bundle file not found: {bundle_path}")

            if bundle_p.suffix.lower() == ".apk":
                print("[!] WARNING: Uploading APK directly. Google Play requires AAB (.aab) for new applications.")
                media = MediaFileUpload(str(bundle_p), mimetype="application/vnd.android.package-archive")
                res = service.edits().apks().upload(packageName=package_name, editId=edit_id, media_body=media).execute()
                version_code = res.get("versionCode")
                print(f"[✓] APK uploaded successfully! Version Code: {version_code}")
            else:
                print(f"[*] Uploading Android App Bundle: {bundle_p.name}...")
                media = MediaFileUpload(str(bundle_p), mimetype="application/octet-stream")
                res = service.edits().bundles().upload(packageName=package_name, editId=edit_id, media_body=media).execute()
                version_code = res.get("versionCode")
                print(f"[✓] App Bundle uploaded successfully! Version Code: {version_code}")

        # Update metadata if requested
        if metadata_dir:
            upload_metadata(service, package_name, edit_id, metadata_dir)

        # Update release track
        if version_code is not None:
            track_body = {
                "releases": [
                    {
                        "name": f"Release {version_code}",
                        "versionCodes": [str(version_code)],
                        "status": status,
                    }
                ]
            }
            service.edits().tracks().update(
                packageName=package_name,
                editId=edit_id,
                track=track,
                body=track_body
            ).execute()
            print(f"[✓] Track '{track}' configured with status='{status}', versionCode={version_code}")

        if validate_only:
            print("[*] Validating edit with Google Play...")
            service.edits().validate(packageName=package_name, editId=edit_id).execute()
            print("[✓] Validation passed! (Not committing due to --validate-only)")
            service.edits().delete(packageName=package_name, editId=edit_id).execute()
        else:
            print("[*] Committing edit to Google Play Console...")
            commit_res = service.edits().commit(packageName=package_name, editId=edit_id).execute()
            print(f"[🎉] SUCCESS! Release committed to Google Play! Commit ID: {commit_res.get('id')}")

    except Exception as e:
        print(f"[!] Error during publication: {e}")
        try:
            service.edits().delete(packageName=package_name, editId=edit_id).execute()
            print("[*] Active edit cleaned up.")
        except Exception:
            pass
        raise e


def main():
    parser = argparse.ArgumentParser(description="Google Play Publisher for AVNC")
    parser.add_argument("--key", default=DEFAULT_KEY, help="Path to Google Play service account JSON key")
    parser.add_argument("--package", default=DEFAULT_PACKAGE, help="Android package name (e.g. com.vncandroid.free)")
    parser.add_argument("--bundle", default=None, help="Path to .aab or .apk artifact")
    parser.add_argument("--track", default="production", choices=["production", "internal", "alpha", "beta"], help="Release track")
    parser.add_argument("--status", default="completed", choices=["completed", "draft", "inProgress", "halted"], help="Release status")
    parser.add_argument("--metadata-dir", default=DEFAULT_METADATA, help="Path to Fastlane/Play metadata folder")
    parser.add_argument("--check", action="store_true", help="Check app registration and tracks without uploading")
    parser.add_argument("--validate-only", action="store_true", help="Validate upload without committing to Play Store")

    args = parser.parse_args()

    service = get_service(args.key)

    if args.check:
        check_app_status(service, args.package)
        return

    if not check_app_status(service, args.package):
        sys.exit(1)

    publish_release(
        service=service,
        package_name=args.package,
        bundle_path=args.bundle,
        track=args.track,
        status=args.status,
        metadata_dir=args.metadata_dir,
        validate_only=args.validate_only
    )


if __name__ == "__main__":
    main()
