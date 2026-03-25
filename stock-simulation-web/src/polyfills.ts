declare global {
  var global: typeof globalThis | undefined
}

if (typeof globalThis.global === 'undefined') {
  globalThis.global = globalThis
}

export {}
