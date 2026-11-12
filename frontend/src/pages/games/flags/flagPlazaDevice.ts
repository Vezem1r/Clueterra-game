interface MemoryAwareNavigator extends Navigator {
  deviceMemory?: number
}

const LOW_MEMORY_GB = 4
const LOW_CORE_COUNT = 4

export function isLowEndDevice() {
  if (typeof navigator === 'undefined') return false
  const memory = (navigator as MemoryAwareNavigator).deviceMemory ?? Number.POSITIVE_INFINITY
  const cores = navigator.hardwareConcurrency || Number.POSITIVE_INFINITY
  return memory <= LOW_MEMORY_GB || cores <= LOW_CORE_COUNT
}
