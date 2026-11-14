const SVG_NAMESPACE = 'http://www.w3.org/2000/svg';

function isAbsent(value) {
    return value === undefined || value === null || value === false;
}

function appendChildren(element, children) {
    for (const child of children.flat(Infinity)) {
        if (!isAbsent(child)) {
            element.append(child instanceof Node ? child : document.createTextNode(String(child)));
        }
    }
}

function setAttributes(element, attributes) {
    for (const [key, value] of Object.entries(attributes ?? {})) {
        if (isAbsent(value)) {
            continue;
        }
        if (key.startsWith('on') && typeof value === 'function') {
            element.addEventListener(key.slice(2), value);
        } else {
            element.setAttribute(key, value === true ? '' : value);
        }
    }
}

export function el(tag, attributes = {}, ...children) {
    const element = document.createElement(tag);
    setAttributes(element, attributes);
    appendChildren(element, children);
    return element;
}

export function svg(tag, attributes = {}, ...children) {
    const element = document.createElementNS(SVG_NAMESPACE, tag);
    setAttributes(element, attributes);
    appendChildren(element, children);
    return element;
}

export function append(element, ...children) {
    appendChildren(element, children);
}

export function replaceChildren(element, ...children) {
    element.replaceChildren();
    appendChildren(element, children);
}
