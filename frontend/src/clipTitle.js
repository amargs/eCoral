function sync(el) {
  const full = el.textContent.trim()
  if (!full || el.clientWidth === 0) {
    el.title = ''
    return
  }
  el.title = el.scrollWidth > el.clientWidth ? full : ''
}

export const vClipTitle = {
  mounted(el) {
    sync(el)
    document.fonts?.ready.then(() => sync(el))
    const observer = new ResizeObserver(() => sync(el))
    observer.observe(el)
    el._clipTitleObserver = observer
  },
  updated(el) {
    sync(el)
  },
  unmounted(el) {
    el._clipTitleObserver?.disconnect()
  },
}
