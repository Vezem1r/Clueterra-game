import './catalogVectorArtwork.css'

const ITALY_OUTLINE = 'M423 95L437 96L446 85L457 98L461 84L475 90L474 83L481 83L482 77L491 79L494 75L511 72L510 76L515 80L537 84L532 88L536 90L533 94L540 102L535 98L527 99L511 108L517 115L513 117L515 129L536 143L544 162L555 172L579 176L576 185L612 201L620 211L618 218L613 216L609 208L594 204L585 218L597 226L597 235L587 238L587 245L578 255L571 255L574 241L581 237L570 212L564 213L558 209L560 206L556 201L548 203L551 199L543 198L538 189L526 189L502 168L493 166L494 163L482 155L483 149L475 133L452 125L441 136L431 138L434 130L422 129L419 123L422 117L415 112L424 109L418 98L423 95Z M569 248L561 264L565 274L561 281L520 263L516 258L521 250L524 253L531 250L539 255L569 248Z M470 203L466 231L457 229L457 234L450 237L446 230L449 217L446 216L446 205L442 202L443 195L448 197L461 189L466 191L470 203Z M481 157L481 160L476 159L481 157Z'

export function ClueCatalogArtwork() {
  return <div className="catalog-vector-art" aria-hidden="true">
    <svg className="catalog-vector-scene" viewBox="0 0 1200 520" preserveAspectRatio="xMaxYMid slice">
      <defs>
        <linearGradient id="clue-vector-back" x2="1" y2="1"><stop stopColor="#0b1026" /><stop offset=".6" stopColor="#192345" /><stop offset="1" stopColor="#0c2730" /></linearGradient>
        <linearGradient id="clue-vector-paper" x2="1" y2="1"><stop stopColor="#34305f" /><stop offset=".55" stopColor="#24254d" /><stop offset="1" stopColor="#162941" /></linearGradient>
        <radialGradient id="clue-vector-glow"><stop stopColor="#b99bea" stopOpacity=".33" /><stop offset="1" stopColor="#b99bea" stopOpacity="0" /></radialGradient>
        <pattern id="clue-vector-grid" width="48" height="48" patternUnits="userSpaceOnUse"><path d="M 48 0 H 0 V 48" fill="none" stroke="#aeb9f2" strokeOpacity=".09" /></pattern>
        <filter id="clue-vector-shadow" x="-35%" y="-35%" width="170%" height="180%"><feDropShadow dx="0" dy="18" stdDeviation="18" floodColor="#020715" floodOpacity=".56" /></filter>
      </defs>
      <rect width="1200" height="520" fill="url(#clue-vector-back)" />
      <rect width="1200" height="520" fill="url(#clue-vector-grid)" />
      <circle cx="971" cy="249" r="352" fill="url(#clue-vector-glow)" />
      <circle cx="969" cy="256" r="302" fill="none" stroke="#b8c9ed" strokeOpacity=".12" strokeWidth="2" />
      <circle cx="969" cy="256" r="253" fill="none" stroke="#b8c9ed" strokeOpacity=".11" strokeWidth="2" strokeDasharray="5 13" />
      <path d="M 638 300 C 748 249 849 262 964 194 C 1059 138 1159 126 1251 151 M 676 368 C 768 314 891 349 1004 287 C 1076 248 1160 260 1232 226" fill="none" stroke="#9bc7d5" strokeOpacity=".13" strokeWidth="3" />
      <path d="M 750 138 C 780 117 809 124 828 145 L 808 159 L 824 178 L 790 187 L 770 172 Z M 1135 329 C 1157 313 1188 322 1207 343 L 1173 365 L 1138 355 Z" fill="#7ca7a7" opacity=".2" />
      <g transform="rotate(9 940 260)" filter="url(#clue-vector-shadow)">
        <rect x="759" y="74" width="333" height="357" rx="26" fill="#15233b" stroke="#98b3ca" strokeOpacity=".42" strokeWidth="3" />
        <path d="M 785 114 H 1040 M 785 389 H 1040" stroke="#b3d2d6" strokeOpacity=".2" strokeWidth="2" />
      </g>
      <g className="catalog-vector-clue-card" filter="url(#clue-vector-shadow)">
        <g transform="translate(989 257) scale(.86) rotate(-8) translate(-989 -242)">
          <rect x="814" y="61" width="350" height="363" rx="28" fill="url(#clue-vector-paper)" stroke="#c4afe9" strokeOpacity=".72" strokeWidth="3" />
          <path d="M 843 105 H 954 M 843 113 H 916" stroke="#e9d7ff" strokeOpacity=".47" strokeWidth="4" strokeLinecap="round" />
          <circle cx="1098" cy="100" r="29" fill="#8a74bd" fillOpacity=".2" stroke="#d7bef9" strokeOpacity=".44" strokeWidth="2" />
          <path d="M 1098 84 V 116 M 1082 100 H 1114" stroke="#e0c5f7" strokeOpacity=".65" strokeWidth="2" />
          <circle cx="989" cy="240" r="113" fill="#a88bd7" fillOpacity=".12" stroke="#d5c0fa" strokeOpacity=".18" strokeWidth="2" />
          <circle cx="989" cy="240" r="87" fill="none" stroke="#e6dcff" strokeOpacity=".21" strokeWidth="2" strokeDasharray="6 10" />
          <text x="989" y="318" textAnchor="middle" fill="#f6e9ff" fontFamily="Georgia, serif" fontSize="204" fontWeight="700">?</text>
          <path d="M 858 369 H 1026 M 858 386 H 977" stroke="#d5c4f0" strokeOpacity=".45" strokeWidth="5" strokeLinecap="round" />
          <circle cx="1114" cy="373" r="18" fill="#76d4c2" fillOpacity=".23" stroke="#8be4ce" strokeOpacity=".75" strokeWidth="2" />
          <path d="M 1106 373 L 1112 379 L 1122 365" fill="none" stroke="#a6f4de" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
        </g>
      </g>
      <path d="M 678 439 H 1168 M 724 456 H 1115" stroke="#c1b9ef" strokeOpacity=".17" strokeWidth="2" />
    </svg>
  </div>
}

export function SilhouetteCatalogArtwork() {
  return <div className="catalog-vector-art" aria-hidden="true">
    <svg className="catalog-vector-scene" viewBox="0 0 720 540" preserveAspectRatio="xMidYMin slice">
      <defs>
        <linearGradient id="shape-vector-back" x2="1" y2="1"><stop stopColor="#081b2b" /><stop offset=".55" stopColor="#0e3944" /><stop offset="1" stopColor="#113a42" /></linearGradient>
        <linearGradient id="shape-vector-tile" x2="1" y2="1"><stop stopColor="#1a5260" /><stop offset=".6" stopColor="#0b3443" /><stop offset="1" stopColor="#092a37" /></linearGradient>
        <linearGradient id="shape-vector-country" x2="1" y2="1"><stop stopColor="#fff4cd" /><stop offset=".53" stopColor="#c6f3df" /><stop offset="1" stopColor="#80d2d1" /></linearGradient>
        <pattern id="shape-vector-grid" width="37" height="37" patternUnits="userSpaceOnUse"><path d="M 37 0 H 0 V 37" fill="none" stroke="#a9e8df" strokeOpacity=".08" /></pattern>
        <filter id="shape-vector-shadow" x="-35%" y="-35%" width="170%" height="190%"><feDropShadow dx="0" dy="15" stdDeviation="14" floodColor="#031623" floodOpacity=".6" /></filter>
        <clipPath id="shape-vector-clip"><rect x="366" y="33" width="304" height="350" rx="24" /></clipPath>
      </defs>
      <rect width="720" height="540" fill="url(#shape-vector-back)" />
      <rect width="720" height="540" fill="url(#shape-vector-grid)" />
      <circle cx="540" cy="211" r="298" fill="#66d5cc" opacity=".06" />
      <path d="M -30 62 C 83 4 181 40 217 126 C 262 232 193 281 103 264 C 19 248 -24 325 52 400 M -21 106 C 105 46 162 90 180 143 C 208 220 139 250 68 233 C -13 213 -52 319 12 373 M 26 20 C 137 -20 226 36 245 115 C 269 217 215 314 133 303" fill="none" stroke="#92d7d5" strokeOpacity=".16" strokeWidth="2" />
      <g transform="rotate(8 487 218)" filter="url(#shape-vector-shadow)"><rect x="336" y="65" width="297" height="339" rx="25" fill="#0d2a39" stroke="#91dcd5" strokeOpacity=".36" strokeWidth="3" /></g>
      <g transform="rotate(-5 518 205)" filter="url(#shape-vector-shadow)">
        <rect x="366" y="33" width="304" height="350" rx="24" fill="url(#shape-vector-tile)" stroke="#a9f4e7" strokeOpacity=".82" strokeWidth="3" />
        <g clipPath="url(#shape-vector-clip)">
          <rect x="366" y="33" width="304" height="350" fill="url(#shape-vector-grid)" />
          <circle cx="520" cy="204" r="128" fill="none" stroke="#d4f7e6" strokeOpacity=".24" strokeWidth="2" strokeDasharray="3 10" />
          <circle cx="520" cy="204" r="98" fill="none" stroke="#d4f7e6" strokeOpacity=".15" strokeWidth="2" />
          <path d={ITALY_OUTLINE} transform="translate(8 10)" fill="#011d2a" opacity=".63" />
          <path d={ITALY_OUTLINE} fill="url(#shape-vector-country)" stroke="#fff7d9" strokeWidth="2" strokeLinejoin="round" />
          <path d="M 390 64 H 422 M 390 64 V 96 M 646 64 H 614 M 646 64 V 96 M 390 348 H 422 M 390 348 V 316 M 646 348 H 614 M 646 348 V 316" fill="none" stroke="#dcfff0" strokeOpacity=".6" strokeWidth="3" strokeLinecap="round" />
        </g>
      </g>
    </svg>
  </div>
}

export function FlagCatalogArtwork() {
  return <div className="catalog-vector-art" aria-hidden="true">
    <svg className="catalog-vector-scene" viewBox="0 0 720 540" preserveAspectRatio="xMidYMin slice">
      <defs>
        <linearGradient id="flag-vector-back" x2="1" y2="1"><stop stopColor="#13162d" /><stop offset=".54" stopColor="#34223d" /><stop offset="1" stopColor="#182b3e" /></linearGradient>
        <radialGradient id="flag-vector-halo"><stop stopColor="#ebaa7c" stopOpacity=".25" /><stop offset="1" stopColor="#ebaa7c" stopOpacity="0" /></radialGradient>
        <pattern id="flag-vector-grid" width="42" height="42" patternUnits="userSpaceOnUse"><path d="M 42 0 H 0 V 42" fill="none" stroke="#dfb7b2" strokeOpacity=".07" /></pattern>
        <filter id="flag-vector-shadow" x="-30%" y="-30%" width="160%" height="180%"><feDropShadow dx="0" dy="12" stdDeviation="10" floodColor="#050c20" floodOpacity=".6" /></filter>
      </defs>
      <rect width="720" height="540" fill="url(#flag-vector-back)" />
      <rect width="720" height="540" fill="url(#flag-vector-grid)" />
      <circle cx="420" cy="208" r="340" fill="url(#flag-vector-halo)" />
      <ellipse cx="392" cy="492" rx="365" ry="77" fill="#0a1729" stroke="#d4b2a0" strokeOpacity=".3" strokeWidth="3" />
      <ellipse cx="392" cy="484" rx="295" ry="49" fill="none" stroke="#e4c48b" strokeOpacity=".31" strokeWidth="2" />
      <path d="M 18 296 C 178 251 288 269 389 258 C 502 245 593 263 731 218 M -10 330 C 169 289 275 305 390 293 C 512 281 623 289 750 256" fill="none" stroke="#efc69d" strokeOpacity=".19" strokeWidth="2" />
      <g filter="url(#flag-vector-shadow)" fill="none" stroke="#efcf91" strokeWidth="5">
        <path d="M 195 58 V 485 M 345 35 V 486 M 500 84 V 488" />
      </g>
      <g fill="#ffe1a5"><circle cx="195" cy="57" r="8" /><circle cx="345" cy="34" r="8" /><circle cx="500" cy="83" r="8" /></g>
      <path d="M 195 486 L 172 506 H 218 Z M 345 487 L 320 507 H 370 Z M 500 489 L 477 507 H 523 Z" fill="#ac8d75" stroke="#e7c991" strokeOpacity=".65" strokeWidth="2" />
      <path d="M 95 441 C 162 407 220 417 289 445 M 410 431 C 474 406 555 409 639 451" fill="none" stroke="#e7b68a" strokeOpacity=".18" strokeWidth="3" />
    </svg>
  </div>
}

export function QuizCatalogArtwork() {
  return <div className="catalog-vector-art" aria-hidden="true">
    <svg className="catalog-vector-scene" viewBox="0 0 720 540" preserveAspectRatio="xMidYMin slice">
      <defs>
        <linearGradient id="quiz-vector-back" x2="1" y2="1"><stop stopColor="#0a1b2a" /><stop offset=".58" stopColor="#173f4a" /><stop offset="1" stopColor="#132c39" /></linearGradient>
        <radialGradient id="quiz-vector-ocean" cx="28%" cy="23%" r="82%"><stop stopColor="#568c92" /><stop offset=".54" stopColor="#1a5665" /><stop offset="1" stopColor="#103744" /></radialGradient>
        <radialGradient id="quiz-vector-glow"><stop stopColor="#b9dfb9" stopOpacity=".23" /><stop offset="1" stopColor="#b9dfb9" stopOpacity="0" /></radialGradient>
        <filter id="quiz-vector-shadow" x="-50%" y="-50%" width="200%" height="200%"><feDropShadow dx="0" dy="8" stdDeviation="9" floodColor="#03151f" floodOpacity=".55" /></filter>
        <clipPath id="quiz-vector-globe-clip"><circle cx="519" cy="258" r="245" /></clipPath>
      </defs>
      <rect width="720" height="540" fill="url(#quiz-vector-back)" />
      <g transform="translate(519 258) scale(.92) translate(-519 -258)">
      <circle cx="524" cy="232" r="350" fill="url(#quiz-vector-glow)" />
      <circle cx="519" cy="258" r="258" fill="none" stroke="#9bd3cf" strokeOpacity=".14" strokeWidth="15" />
      <circle cx="519" cy="258" r="245" fill="url(#quiz-vector-ocean)" stroke="#cae7d5" strokeOpacity=".55" strokeWidth="3" />
      <g clipPath="url(#quiz-vector-globe-clip)">
        <path d="M 279 190 Q 517 129 758 186 M 276 262 Q 518 210 763 263 M 284 343 Q 526 303 759 349 M 378 10 Q 316 249 377 509 M 499 5 Q 458 252 502 513 M 626 9 Q 690 261 629 507" fill="none" stroke="#d4e8d5" strokeOpacity=".17" strokeWidth="2" />
        <path d="M 350 59 L 390 48 L 429 66 L 449 91 L 473 92 L 485 111 L 464 129 L 476 145 L 455 164 L 424 160 L 410 181 L 391 180 L 384 206 L 357 203 L 348 183 L 322 181 L 313 146 L 327 126 L 321 99 Z" fill="#8db4a6" stroke="#d5e5cc" strokeOpacity=".42" strokeWidth="2" />
        <path d="M 420 208 L 478 191 L 503 212 L 526 232 L 516 263 L 499 272 L 487 316 L 464 350 L 453 401 L 427 416 L 409 385 L 413 347 L 390 323 L 385 284 L 397 251 Z" fill="#98b99f" stroke="#d5e5cc" strokeOpacity=".42" strokeWidth="2" />
        <path d="M 498 99 L 548 65 L 614 71 L 658 97 L 708 112 L 754 171 L 739 208 L 697 217 L 664 198 L 626 221 L 592 202 L 563 220 L 534 189 L 508 192 L 491 162 L 511 139 Z" fill="#86aa9c" stroke="#d5e5cc" strokeOpacity=".35" strokeWidth="2" />
        <path d="M 526 330 L 568 323 L 602 345 L 586 377 L 554 391 L 526 369 Z" fill="#88a997" stroke="#d5e5cc" strokeOpacity=".35" strokeWidth="2" />
        <path d="M 354 130 C 425 94 499 112 568 171 C 610 207 625 244 661 268" fill="none" stroke="#fff1c3" strokeOpacity=".88" strokeWidth="4" strokeDasharray="9 12" strokeLinecap="round" />
        <circle cx="354" cy="130" r="10" fill="#ffe9aa" stroke="#fff7d5" strokeWidth="3" />
        <circle cx="661" cy="268" r="10" fill="#ffe9aa" stroke="#fff7d5" strokeWidth="3" />
      </g>
      <g filter="url(#quiz-vector-shadow)" transform="translate(405 116) rotate(-16)">
        <g className="catalog-vector-car">
          <path d="M -35 -7 L -22 -25 H 25 L 37 -7 V 11 H -35 Z" fill="#eaa274" stroke="#ffe0aa" strokeWidth="3" strokeLinejoin="round" />
          <path d="M -16 -21 H 18 L 26 -7 H -24 Z" fill="#235669" stroke="#b7d7d1" strokeWidth="2" />
          <circle cx="-23" cy="13" r="8" fill="#142a32" stroke="#d6c6a2" strokeWidth="3" /><circle cx="24" cy="13" r="8" fill="#142a32" stroke="#d6c6a2" strokeWidth="3" />
          <path d="M -33 1 H -24 M 26 1 H 35" stroke="#fff1c5" strokeWidth="4" strokeLinecap="round" />
        </g>
      </g>
      <g filter="url(#quiz-vector-shadow)" transform="translate(604 207) rotate(28)">
        <g className="catalog-vector-plane">
          <path d="M 0 -45 C 5 -45 8 -40 8 -32 L 11 -7 L 47 14 V 25 L 10 18 L 8 36 L 24 47 V 53 L 0 46 L -24 53 V 47 L -8 36 L -10 18 L -47 25 V 14 L -11 -7 L -8 -32 C -8 -40 -5 -45 0 -45 Z" fill="#fff1c9" stroke="#e9aa76" strokeWidth="3" strokeLinejoin="round" />
          <path d="M 0 -37 C 4 -37 5 -31 5 -25 V -7 H -5 V -25 C -5 -31 -4 -37 0 -37 Z" fill="#3d7180" />
        </g>
      </g>
      <path d="M 286 461 H 712 M 321 482 H 682" stroke="#c8e0ce" strokeOpacity=".16" strokeWidth="2" />
      </g>
    </svg>
  </div>
}
