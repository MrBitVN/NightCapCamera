import Foundation
import UIKit
import Photos

public enum IOSImageSaver {
    private static let albumName = "NightCap"

    public static func saveImageToGallery(_ image: UIImage, modeName: String, completion: @escaping (Bool) -> Void) {
        PHPhotoLibrary.requestAuthorization(for: .addOnly) { status in
            guard status == .authorized || status == .limited else {
                DispatchQueue.main.async { completion(false) }
                return
            }

            PHPhotoLibrary.shared().performChanges({
                let creationRequest = PHAssetChangeRequest.creationRequestForAsset(from: image)
                let placeholder = creationRequest.placeholderForCreatedAsset

                // Add to NightCap album if available
                if let album = getOrCreateAlbum(), let placeholder = placeholder {
                    let albumChangeRequest = PHAssetCollectionChangeRequest(for: album)
                    albumChangeRequest?.addAssets([placeholder] as NSArray)
                }
            }) { success, error in
                DispatchQueue.main.async {
                    if let error = error {
                        print("Failed to save image: \(error)")
                    }
                    completion(success)
                }
            }
        }
    }

    public static func saveVideoToGallery(videoURL: URL, completion: @escaping (Bool) -> Void) {
        PHPhotoLibrary.requestAuthorization(for: .addOnly) { status in
            guard status == .authorized || status == .limited else {
                DispatchQueue.main.async { completion(false) }
                return
            }

            PHPhotoLibrary.shared().performChanges({
                let creationRequest = PHAssetChangeRequest.creationRequestForAssetFromVideo(atFileURL: videoURL)
                let placeholder = creationRequest?.placeholderForCreatedAsset

                if let album = getOrCreateAlbum(), let placeholder = placeholder {
                    let albumChangeRequest = PHAssetCollectionChangeRequest(for: album)
                    albumChangeRequest?.addAssets([placeholder] as NSArray)
                }
            }) { success, error in
                // Clean up temporary video file
                try? FileManager.default.removeItem(at: videoURL)

                DispatchQueue.main.async {
                    if let error = error {
                        print("Failed to save video: \(error)")
                    }
                    completion(success)
                }
            }
        }
    }

    private static func getOrCreateAlbum() -> PHAssetCollection? {
        let fetchOptions = PHFetchOptions()
        fetchOptions.predicate = NSPredicate(format: "title = %@", albumName)
        let collection = PHAssetCollection.fetchAssetCollections(with: .album, subtype: .any, options: fetchOptions)

        if let first = collection.firstObject {
            return first
        }

        var albumPlaceholder: PHObjectPlaceholder?
        try? PHPhotoLibrary.shared().performChangesAndWait {
            let createRequest = PHAssetCollectionChangeRequest.creationRequestForAssetCollection(withTitle: albumName)
            albumPlaceholder = createRequest.placeholderForCreatedAssetCollection
        }

        if let placeholder = albumPlaceholder {
            return PHAssetCollection.fetchAssetCollections(withLocalIdentifiers: [placeholder.localIdentifier], options: nil).firstObject
        }

        return nil
    }
}
