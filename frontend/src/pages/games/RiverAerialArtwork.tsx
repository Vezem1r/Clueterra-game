import './catalogConstructionArtwork.css'

const WATER_SHAPE = 'M 280 -36 C 256 19 277 68 329 104 C 385 143 383 165 358 199 C 325 244 366 290 428 302 C 485 313 515 274 563 282 C 624 292 660 327 748 302 L 748 394 C 651 404 612 365 559 364 C 499 361 470 397 410 377 C 329 350 282 281 306 217 C 325 167 257 137 239 78 C 225 28 238 -12 247 -36 Z'
const TREE_CANOPIES = [
  [146, 118, .9], [182, 138, 1.05], [212, 105, .82], [152, 188, .88],
  [453, 112, .78], [496, 127, .86], [626, 136, 1], [658, 168, .83],
]

export function RiverAerialArtwork() {
  return <div className="catalog-river-aerial-art" aria-hidden="true">
    <svg className="catalog-river-aerial-scene" viewBox="0 0 720 540" preserveAspectRatio="xMidYMid slice">
      <defs>
        <linearGradient id="aerial-land" x1="0" y1="0" x2="1" y2="1"><stop stopColor="#183e35" /><stop offset=".54" stopColor="#0c302e" /><stop offset="1" stopColor="#0a2630" /></linearGradient>
        <linearGradient id="aerial-water" x1="0" y1="0" x2="1" y2="1"><stop stopColor="#75d9d8" /><stop offset=".26" stopColor="#42b9ce" /><stop offset=".72" stopColor="#157caa" /><stop offset="1" stopColor="#145c8b" /></linearGradient>
        <linearGradient id="aerial-deep" x1="0" y1="0" x2="1" y2="1"><stop stopColor="#b4f3de" stopOpacity=".2" /><stop offset="1" stopColor="#063656" stopOpacity=".35" /></linearGradient>
        <radialGradient id="aerial-light"><stop stopColor="#9fdfc2" stopOpacity=".27" /><stop offset="1" stopColor="#9fdfc2" stopOpacity="0" /></radialGradient>
        <filter id="aerial-shadow" x="-30%" y="-30%" width="160%" height="160%"><feDropShadow dx="0" dy="9" stdDeviation="10" floodColor="#041c1e" floodOpacity=".5" /></filter>
        <clipPath id="aerial-water-clip"><path d={WATER_SHAPE} /></clipPath>
      </defs>

      <rect width="720" height="540" fill="url(#aerial-land)" />
      <circle cx="523" cy="145" r="290" fill="url(#aerial-light)" />
      <path d="M 0 28 C 74 4 149 27 211 9 C 259 -4 299 9 323 38 M -10 64 C 68 41 142 63 203 44 C 260 27 301 42 327 72 M -10 103 C 74 83 145 100 204 82 C 247 68 280 73 300 90 M 8 304 C 95 279 179 312 250 293 C 283 284 306 271 325 249 M -4 332 C 96 304 181 339 256 318 C 286 308 313 294 331 274" fill="none" stroke="#a6d5af" strokeOpacity=".16" strokeWidth="2" />
      <path d="M 505 10 C 562 25 628 -4 734 21 M 515 40 C 580 57 638 29 737 54 M 525 70 C 589 90 649 61 742 87 M 540 100 C 600 120 652 97 746 122 M 541 130 C 609 150 669 127 747 157 M 578 390 C 639 410 687 384 745 403 M 571 415 C 635 441 688 416 749 437" fill="none" stroke="#b0d8b0" strokeOpacity=".16" strokeWidth="2" />
      <path d="M 104 114 C 159 96 220 117 258 142 C 280 157 290 185 296 213 M 84 147 C 149 128 213 153 254 179 C 277 194 284 224 287 248 M 76 185 C 143 168 207 194 245 223 C 267 240 278 269 280 293" fill="none" stroke="#a6d5af" strokeOpacity=".11" strokeWidth="2" />
      <path d="M 491 179 C 540 159 588 169 625 187 C 657 204 679 214 731 207 M 509 209 C 549 193 597 204 628 220 C 667 240 687 244 737 238" fill="none" stroke="#b0d8b0" strokeOpacity=".13" strokeWidth="2" />

      <path d={WATER_SHAPE} fill="#062e3d" stroke="#071f29" strokeWidth="27" strokeOpacity=".5" filter="url(#aerial-shadow)" />
      <path d={WATER_SHAPE} fill="url(#aerial-water)" stroke="#a9ecdc" strokeOpacity=".68" strokeWidth="3" />
      <g clipPath="url(#aerial-water-clip)">
        <path d="M 277 -30 C 255 33 298 91 347 126 C 385 154 386 185 357 222 C 339 263 377 315 432 326 C 492 336 519 305 565 306 C 628 312 671 353 749 337" fill="none" stroke="url(#aerial-deep)" strokeWidth="52" strokeLinecap="round" />
        <path className="catalog-river-waterline" d="M 281 -28 C 262 35 306 88 347 119 C 395 155 392 181 361 218 C 335 255 376 307 429 321 C 487 339 513 300 563 305 C 625 314 663 348 742 332" fill="none" stroke="#f0fff4" strokeOpacity=".5" strokeWidth="3" strokeDasharray="21 37" strokeLinecap="round" />
        <path className="catalog-river-waterline" style={{ animationDelay: '-1.6s' }} d="M 259 -25 C 246 34 287 96 321 123 C 371 163 368 184 339 215 C 314 249 354 319 415 341 C 482 367 511 323 563 329 C 627 337 665 372 742 356" fill="none" stroke="#d8fff5" strokeOpacity=".3" strokeWidth="2" strokeDasharray="21 37" strokeLinecap="round" />
        <path className="catalog-river-waterline" style={{ animationDelay: '-3s' }} d="M 304 -30 C 286 28 321 71 370 109 C 413 141 413 175 389 211 C 365 242 402 280 444 287 C 498 297 531 256 574 263 C 634 272 678 304 742 282" fill="none" stroke="#ddfff4" strokeOpacity=".35" strokeWidth="2.5" strokeDasharray="21 37" strokeLinecap="round" />
        <path d="M 370 155 C 385 168 380 189 369 204 M 422 292 C 440 299 455 300 469 297 M 597 291 C 615 293 629 297 642 303" fill="none" stroke="#ebfff8" strokeOpacity=".48" strokeWidth="2" strokeLinecap="round" />
      </g>

      <path d="M 511 281 C 530 273 549 274 565 283 C 579 292 585 306 582 316 C 563 308 547 304 533 307 C 521 309 512 299 511 281 Z" fill="#377f62" stroke="#d4edbc" strokeOpacity=".76" strokeWidth="3" />
      <path d="M 527 285 C 539 280 554 282 567 291 M 540 299 C 552 294 565 298 573 305" fill="none" stroke="#def3ca" strokeOpacity=".52" strokeWidth="2" />
      {TREE_CANOPIES.map(([x, y, size], index) => <g key={index} transform={`translate(${x} ${y}) scale(${size})`}>
        <ellipse cx="2" cy="5" rx="18" ry="14" fill="#061e24" opacity=".34" />
        <circle cx="-8" cy="0" r="10" fill="#2d7051" />
        <circle cx="2" cy="-6" r="12" fill="#39855e" />
        <circle cx="11" cy="5" r="9" fill="#286a4e" />
        <path d="M -11 -1 C -8 -7 -1 -9 4 -8 M 4 4 C 8 1 12 1 15 4" fill="none" stroke="#b4e3b4" strokeOpacity=".5" strokeWidth="2" strokeLinecap="round" />
      </g>)}
    </svg>
  </div>
}
