package org.example.project.storage

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithBytes
import platform.Foundation.writeToFile
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentInteractionController
import platform.UIKit.UIDocumentInteractionControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
class IosDocumentStorage : DocumentStorage {

    // Må holdes i live så lenge forhåndsvisningen er åpen
    private var activeController: UIDocumentInteractionController? = null

    private val delegate = object : NSObject(), UIDocumentInteractionControllerDelegateProtocol {
        override fun documentInteractionControllerViewControllerForPreview(
            controller: UIDocumentInteractionController,
        ): UIViewController =
            UIApplication.sharedApplication.keyWindow?.rootViewController ?: UIViewController()
    }

    private fun documentsDir(): String {
        val base = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory, NSUserDomainMask, true,
        ).first() as String
        val dir = "$base/documents"
        NSFileManager.defaultManager.createDirectoryAtPath(
            dir, withIntermediateDirectories = true, attributes = null, error = null,
        )
        return dir
    }

    override fun save(fileName: String, bytes: ByteArray): String {
        val path = "${documentsDir()}/$fileName"
        val data = if (bytes.isEmpty()) {
            NSData()
        } else {
            bytes.usePinned { pinned ->
                NSData.dataWithBytes(pinned.addressOf(0), bytes.size.toULong())
            }
        }
        data.writeToFile(path, atomically = true)
        return path
    }

    override fun exists(path: String): Boolean =
        NSFileManager.defaultManager.fileExistsAtPath(path)

    override fun delete(path: String) {
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    }

    override fun openPdf(path: String): Boolean {
        if (!exists(path)) return false
        val controller = UIDocumentInteractionController
            .interactionControllerWithURL(NSURL.fileURLWithPath(path))
        controller.delegate = delegate
        activeController = controller
        return controller.presentPreviewAnimated(true)
    }
}
