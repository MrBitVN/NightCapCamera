import Foundation
import UIKit
import Photos

public enum IOSImageSaver {
    private static let albumName = "NightCap"

    public static func saveImageToGallery(_ image: UIImage, modeName: String, completion: @escaping (Bool) -> Void) {
        PHPhotoLibrary.requestAuthorization(for: .readWrite) { status in
            guard status == .authorized || status == .limited else {
                // Fallback: try saving directly with addOnly if readWrite was restricted
                PHPhotoLibrary.requestAuthorization(for: .addOnly) { addStatus in
                    guard addStatus == .authorized || addStatus == .limited else {
                        DispatchQueue.main.async { completion(false) }
                        return
                    }
                    PHPhotoLibrary.shared().performChanges({
                        PHAssetChangeRequest.creationRequestForAsset(from: image)
                    }) { success, error in
                        if let error = error {
                            print("Failed to save image (addOnly): \(error)")
                        }
                        DispatchQueue.main.async { completion(success) }
                    }
                }
                return
            }

            let existingAlbum = findAlbum()

            PHPhotoLibrary.shared().performChanges({
                let creationRequest = PHAssetChangeRequest.creationRequestForAsset(from: image)
                guard let placeholder = creationRequest.placeholderForCreatedAsset else { return }

                if let album = existingAlbum {
                    let albumChangeRequest = PHAssetCollectionChangeRequest(for: album)
                    albumChangeRequest?.addAssets([placeholder] as NSArray)
                } else {
                    let createAlbumRequest = PHAssetCollectionChangeRequest.creationRequestForAssetCollection(withTitle: albumName)
                    createAlbumRequest.addAssets([placeholder] as NSArray)
                }
            }) { success, error in
                if let error = error {
                    print("Failed to save image: \(error)")
                }
                DispatchQueue.main.async {
                    completion(success)
                }
            }
        }
    }

    public static func saveVideoToGallery(videoURL: URL, completion: @escaping (Bool) -> Void) {
        PHPhotoLibrary.requestAuthorization(for: .readWrite) { status in
            guard status == .authorized || status == .limited else {
                PHPhotoLibrary.requestAuthorization(for: .addOnly) { addStatus in
                    guard addStatus == .authorized || addStatus == .limited else {
                        try? FileManager.default.removeItem(at: videoURL)
                        DispatchQueue.main.async { completion(false) }
                        return
                    }
                    PHPhotoLibrary.shared().performChanges({
                        PHAssetChangeRequest.creationRequestForAssetFromVideo(atFileURL: videoURL)
                    }) { success, error in
                        try? FileManager.default.removeItem(at: videoURL)
                        if let error = error {
                            print("Failed to save video (addOnly): \(error)")
                        }
                        DispatchQueue.main.async { completion(success) }
                    }
                }
                return
            }

            let existingAlbum = findAlbum()

            PHPhotoLibrary.shared().performChanges({
                let creationRequest = PHAssetChangeRequest.creationRequestForAssetFromVideo(atFileURL: videoURL)
                guard let placeholder = creationRequest?.placeholderForCreatedAsset else { return }

                if let album = existingAlbum {
                    let albumChangeRequest = PHAssetCollectionChangeRequest(for: album)
                    albumChangeRequest?.addAssets([placeholder] as NSArray)
                } else {
                    let createAlbumRequest = PHAssetCollectionChangeRequest.creationRequestForAssetCollection(withTitle: albumName)
                    createAlbumRequest.addAssets([placeholder] as NSArray)
                }
            }) { success, error in
                try? FileManager.default.removeItem(at: videoURL)
                if let error = error {
                    print("Failed to save video: \(error)")
                }
                DispatchQueue.main.async {
                    completion(success)
                }
            }
        }
    }

    private static func findAlbum() -> PHAssetCollection? {
        let fetchOptions = PHFetchOptions()
        fetchOptions.predicate = NSPredicate(format: "title = %@", albumName)
        let collection = PHAssetCollection.fetchAssetCollections(with: .album, subtype: .any, options: fetchOptions)
        return collection.firstObject
    }
}
