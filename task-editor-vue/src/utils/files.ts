/**
 * 导出 / 导入的字节级小工具。
 *
 * <p>导出可能是单个 `yml`（一条定义）也可能是 `zip`（多条），两种都由<b>同一串字节</b>
 * 回来；用魔数区分比读 `Content-Type` 可靠——浏览器给 zip 的类型五花八门
 * （`application/zip`、`x-zip-compressed`、`octet-stream`），后端也是这么判的。
 */

/** zip 的魔数：`PK`。 */
export function isZipBytes(bytes: ArrayBuffer): boolean {
  const head = new Uint8Array(bytes.slice(0, 2))
  return head.length === 2 && head[0] === 0x50 && head[1] === 0x4b
}

export function decodeBytes(bytes: ArrayBuffer): string {
  return new TextDecoder().decode(bytes)
}

export function downloadBytes(bytes: ArrayBuffer, fileName: string, mime: string): void {
  downloadBlob(new Blob([bytes], { type: mime }), fileName)
}

/** 触发浏览器下载；立即 revoke 在部分浏览器上会得到空文件，因此延后释放。 */
export function downloadBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}
