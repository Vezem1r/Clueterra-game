import { useEffect, useRef } from 'react'
import { useFrame, useThree } from '@react-three/fiber'

const MS_PER_SECOND = 1000

export function ShaderWarmup({ onReady }: { onReady: () => void }) {
  const gl = useThree(state => state.gl)
  const scene = useThree(state => state.scene)
  const camera = useThree(state => state.camera)

  useEffect(() => {
    let active = true
    gl.compileAsync(scene, camera)
      .catch(() => undefined)
      .finally(() => {
        if (active) onReady()
      })
    return () => {
      active = false
    }
  }, [gl, scene, camera, onReady])

  return null
}

export function FrameCap({ fps }: { fps: number }) {
  const invalidate = useThree(state => state.invalidate)

  useEffect(() => {
    const timer = window.setInterval(() => invalidate(), MS_PER_SECOND / fps)
    return () => window.clearInterval(timer)
  }, [invalidate, fps])

  return null
}

export function AfterFrames({ frames, onReach }: { frames: number; onReach: () => void }) {
  const counted = useRef(0)

  useFrame(() => {
    if (counted.current > frames) return
    counted.current += 1
    if (counted.current > frames) onReach()
  })

  return null
}
