import { AnimatePresence, motion } from 'framer-motion'
import jsQR from 'jsqr'
import { Camera, CameraOff, QrCode, ScanLine } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Alert } from '../ui/alert'
import { Button } from '../ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../ui/card'

type BarcodeResult = {
  rawValue?: string
}

type BarcodeDetectorLike = {
  detect: (source: ImageBitmapSource) => Promise<BarcodeResult[]>
}

type BarcodeDetectorCtor = new (options?: { formats?: string[] }) => BarcodeDetectorLike

type CameraQrScannerProps = {
  onScan: (value: string) => void
}

export function CameraQrScanner({ onScan }: CameraQrScannerProps) {
  const videoRef = useRef<HTMLVideoElement | null>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const rafRef = useRef<number | null>(null)
  const canvasRef = useRef<HTMLCanvasElement | null>(null)
  const detectorRef = useRef<BarcodeDetectorLike | null>(null)

  const [scanning, setScanning] = useState(false)
  const [scanError, setScanError] = useState<string | null>(null)
  const [lastScannedValue, setLastScannedValue] = useState<string | null>(null)

  const supportsScanning = useMemo(() => {
    if (typeof window === 'undefined' || typeof navigator === 'undefined') {
      return false
    }

    return typeof navigator.mediaDevices?.getUserMedia === 'function'
  }, [])

  useEffect(() => {
    if (typeof document !== 'undefined' && !canvasRef.current) {
      canvasRef.current = document.createElement('canvas')
    }

    return () => {
      stopScanner()
    }
  }, [])

  function stopScanner() {
    if (rafRef.current) {
      cancelAnimationFrame(rafRef.current)
      rafRef.current = null
    }

    if (streamRef.current) {
      for (const track of streamRef.current.getTracks()) {
        track.stop()
      }
      streamRef.current = null
    }

    if (videoRef.current) {
      videoRef.current.srcObject = null
    }

    setScanning(false)
  }

  async function startScanner() {
    if (!supportsScanning) {
      setScanError('Camera QR scanning is not supported in this browser.')
      return
    }

    setScanError(null)
    setLastScannedValue(null)

    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: {
          facingMode: {
            ideal: 'environment',
          },
        },
        audio: false,
      })

      streamRef.current = stream

      if (!videoRef.current) {
        throw new Error('Unable to access camera preview.')
      }

      videoRef.current.srcObject = stream
      await videoRef.current.play()

      const detectorConstructor = (window as Window & { BarcodeDetector?: BarcodeDetectorCtor }).BarcodeDetector
      detectorRef.current = detectorConstructor
        ? new detectorConstructor({ formats: ['qr_code'] })
        : null

      setScanning(true)
      scanLoop()
    } catch (error) {
      stopScanner()
      setScanError(error instanceof Error ? error.message : 'Failed to start scanner.')
    }
  }

  function scanLoop() {
    const video = videoRef.current
    const canvas = canvasRef.current

    if (!video || !canvas || video.readyState < 2) {
      rafRef.current = requestAnimationFrame(scanLoop)
      return
    }

    const context = canvas.getContext('2d', { willReadFrequently: true })
    if (!context) {
      setScanError('Unable to initialize scan context.')
      stopScanner()
      return
    }

    canvas.width = video.videoWidth
    canvas.height = video.videoHeight
    context.drawImage(video, 0, 0, canvas.width, canvas.height)

    void decodeFrame(canvas, context).then((decodedValue) => {
      if (!decodedValue) {
        rafRef.current = requestAnimationFrame(scanLoop)
        return
      }

      setLastScannedValue(decodedValue)
      onScan(decodedValue)
      stopScanner()
    }).catch(() => {
      rafRef.current = requestAnimationFrame(scanLoop)
    })
  }

  async function decodeFrame(canvas: HTMLCanvasElement, context: CanvasRenderingContext2D): Promise<string | null> {
    const detector = detectorRef.current

    if (detector) {
      const results = await detector.detect(canvas)
      const decodedValue = results.find((item) => typeof item.rawValue === 'string' && item.rawValue.trim().length > 0)?.rawValue

      return decodedValue?.trim() ?? null
    }

    const imageData = context.getImageData(0, 0, canvas.width, canvas.height)
    const decodedQr = jsQR(imageData.data, imageData.width, imageData.height, {
      inversionAttempts: 'attemptBoth',
    })

    return decodedQr?.data?.trim() ?? null
  }

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between gap-2">
          <CardTitle className="text-base">Scan QR to Pay</CardTitle>
          <QrCode className="h-4 w-4 text-primary" />
        </div>
        <CardDescription>
          Use camera scan to auto-fill destination wallet ID for transfers.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="overflow-hidden rounded-xl border bg-black/90">
          <video ref={videoRef} className="aspect-video w-full object-cover" playsInline muted />
        </div>

        {!supportsScanning ? (
          <Alert>Camera QR scanning is unavailable here. Use copy/paste wallet ID instead.</Alert>
        ) : null}

        {scanError ? <Alert>{scanError}</Alert> : null}

        <div className="flex flex-wrap gap-2">
          {!scanning ? (
            <Button type="button" variant="outline" onClick={() => void startScanner()} disabled={!supportsScanning}>
              <Camera className="mr-1 h-4 w-4" />
              Start Camera Scan
            </Button>
          ) : (
            <Button type="button" variant="outline" onClick={stopScanner}>
              <CameraOff className="mr-1 h-4 w-4" />
              Stop Scan
            </Button>
          )}
        </div>

        <AnimatePresence>
          {scanning ? (
            <motion.p
              className="flex items-center gap-1 text-xs text-muted-foreground"
              initial={{ opacity: 0, y: 4 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -4 }}
            >
              <ScanLine className="h-3.5 w-3.5" />
              Scanning for wallet QR…
            </motion.p>
          ) : null}
        </AnimatePresence>

        {lastScannedValue ? (
          <p className="rounded-lg border border-emerald-200 bg-emerald-50 p-2 text-xs text-emerald-700">
            Scanned wallet: {lastScannedValue}
          </p>
        ) : null}
      </CardContent>
    </Card>
  )
}
