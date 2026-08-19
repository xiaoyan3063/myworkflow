import { JSDOM } from 'jsdom'

const dom = new JSDOM('<!doctype html><html><body></body></html>', { pretendToBeVisual: true })

global.window = dom.window
global.document = dom.window.document
global.navigator = dom.window.navigator
global.HTMLElement = dom.window.HTMLElement
global.SVGElement = dom.window.SVGElement
global.Element = dom.window.Element
global.Node = dom.window.Node
global.Event = dom.window.Event
global.MutationObserver = dom.window.MutationObserver
global.getComputedStyle = dom.window.getComputedStyle
global.requestAnimationFrame = (fn) => setTimeout(fn, 0)
global.cancelAnimationFrame = (id) => clearTimeout(id)
