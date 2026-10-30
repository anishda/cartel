import AVFoundation
import UIKit
import VisionKit
import Shared

/// Scan en direct du texte avec VisionKit (`DataScannerViewController`).
/// Branché sur le code Kotlin par `LiveScannerRegistry` au démarrage de l'app.
final class DataScannerProvider: NSObject, LiveScannerProvider {
    // Compose appelle ces méthodes depuis le fil principal ; VisionKit exige l'isolation « main actor ».
    func isSupported() -> Bool {
        MainActor.assumeIsolated { DataScannerViewController.isSupported }
    }

    func makeViewController(onText: @escaping (LiveTextFrame) -> Void) -> UIViewController {
        MainActor.assumeIsolated { LiveScannerHostController(onText: onText) }
    }
}

/// Contrôleur d'accueil : demande l'accès à la caméra, puis embarque le scanner VisionKit et
/// transmet les lignes visibles, avec leur position, à chaque changement.
final class LiveScannerHostController: UIViewController, DataScannerViewControllerDelegate {
    private let onText: (LiveTextFrame) -> Void
    private var scanner: DataScannerViewController?

    init(onText: @escaping (LiveTextFrame) -> Void) {
        self.onText = onText
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) n'est pas utilisé")
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        guard scanner == nil else { return }
        AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
            DispatchQueue.main.async {
                if granted { self?.startScanner() }
            }
        }
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        scanner?.stopScanning()
    }

    private func startScanner() {
        guard scanner == nil, DataScannerViewController.isSupported, DataScannerViewController.isAvailable else { return }
        let scanner = DataScannerViewController(
            recognizedDataTypes: [.text()],
            qualityLevel: .accurate,
            recognizesMultipleItems: true,
            isHighFrameRateTrackingEnabled: false,
            isPinchToZoomEnabled: true,
            isGuidanceEnabled: false,
            isHighlightingEnabled: true
        )
        scanner.delegate = self
        addChild(scanner)
        scanner.view.frame = view.bounds
        scanner.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(scanner.view)
        scanner.didMove(toParent: self)
        self.scanner = scanner
        try? scanner.startScanning()
    }

    func dataScanner(_ dataScanner: DataScannerViewController, didAdd addedItems: [RecognizedItem], allItems: [RecognizedItem]) {
        publish(allItems)
    }

    func dataScanner(_ dataScanner: DataScannerViewController, didUpdate updatedItems: [RecognizedItem], allItems: [RecognizedItem]) {
        publish(allItems)
    }

    func dataScanner(_ dataScanner: DataScannerViewController, didRemove removedItems: [RecognizedItem], allItems: [RecognizedItem]) {
        publish(allItems)
    }

    /// Les lignes lues avec leur position dans la vue (normalisée, origine en haut à gauche) : c'est le code
    /// Kotlin qui décide ce qui est le cartel et dans quel ordre le lire.
    private func publish(_ items: [RecognizedItem]) {
        let size = scanner?.view.bounds.size ?? view.bounds.size
        guard size.width > 0, size.height > 0 else { return }
        var lines: [LiveLine] = []
        for item in items {
            guard case .text(let text) = item else { continue }
            let b = text.bounds
            let xs = [b.topLeft.x, b.topRight.x, b.bottomLeft.x, b.bottomRight.x]
            let ys = [b.topLeft.y, b.topRight.y, b.bottomLeft.y, b.bottomRight.y]
            guard let left = xs.min(), let right = xs.max(), let top = ys.min(), let bottom = ys.max() else { continue }
            // Un bloc de plusieurs lignes est découpé en parts égales en hauteur.
            let parts = text.transcript.split(separator: "\n", omittingEmptySubsequences: true).map(String.init)
            let step = (bottom - top) / CGFloat(max(parts.count, 1))
            for (index, part) in parts.enumerated() {
                let partTop = top + step * CGFloat(index)
                lines.append(LiveLine(
                    text: part,
                    left: Float(left / size.width),
                    top: Float(partTop / size.height),
                    right: Float(right / size.width),
                    bottom: Float((partTop + step) / size.height),
                    confidence: 1
                ))
            }
        }
        onText(LiveTextFrame(lines: lines, width: Int32(size.width), height: Int32(size.height)))
    }
}
