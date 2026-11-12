import './catalogConstructionArtwork.css'

const CRANE_BRACES = Array.from({ length: 12 }, (_, index) => index)
const BUILDING_WINDOWS = Array.from({ length: 5 }, (_, row) => row)

export function BudgetConstructionArtwork() {
  return <div className="catalog-construction-art" aria-hidden="true">
    <svg className="catalog-construction-scene" viewBox="0 0 1200 520" preserveAspectRatio="xMaxYMid slice">
      <defs>
        <linearGradient id="construction-back" x1="0" y1="0" x2="1" y2="1">
          <stop stopColor="#092622" /><stop offset=".58" stopColor="#15483e" /><stop offset="1" stopColor="#102820" />
        </linearGradient>
        <radialGradient id="construction-halo"><stop stopColor="#6bd4a5" stopOpacity=".22" /><stop offset="1" stopColor="#6bd4a5" stopOpacity="0" /></radialGradient>
        <radialGradient id="construction-lamp"><stop stopColor="#ffe3a0" stopOpacity=".45" /><stop offset="1" stopColor="#ffe3a0" stopOpacity="0" /></radialGradient>
        <linearGradient id="construction-glass" x1="0" y1="0" x2="1" y2="1"><stop stopColor="#50caa2" /><stop offset=".5" stopColor="#23846e" /><stop offset="1" stopColor="#12564e" /></linearGradient>
        <linearGradient id="construction-steel" x1="0" y1="0" x2="1" y2="1"><stop stopColor="#f1d798" /><stop offset="1" stopColor="#a47c46" /></linearGradient>
        <pattern id="construction-grid" width="42" height="42" patternUnits="userSpaceOnUse"><path d="M 42 0 H 0 V 42" fill="none" stroke="#a9e5c6" strokeOpacity=".18" /><circle cx="0" cy="0" r="1.3" fill="#c5f2d4" opacity=".2" /></pattern>
        <pattern id="construction-major-grid" width="168" height="168" patternUnits="userSpaceOnUse"><path d="M 168 0 H 0 V 168" fill="none" stroke="#a9e5c6" strokeOpacity=".13" strokeWidth="2" /></pattern>
        <filter id="construction-shadow" x="-40%" y="-60%" width="180%" height="220%"><feDropShadow dx="0" dy="16" stdDeviation="13" floodColor="#021712" floodOpacity=".55" /></filter>
      </defs>

      <rect width="1200" height="520" fill="url(#construction-back)" />
      <rect width="1200" height="520" fill="url(#construction-grid)" />
      <rect width="1200" height="520" fill="url(#construction-major-grid)" />
      <circle cx="965" cy="265" r="315" fill="url(#construction-halo)" />
      <path d="M 600 479 H 1200 V 520 H 600 Z" fill="#102f2b" />
      <path d="M 600 479 H 1200 M 615 498 H 1190 M 645 516 H 1160" fill="none" stroke="#bde3c7" strokeOpacity=".17" strokeWidth="2" />
      <g transform="translate(57 50) scale(.94)">
      <path d="M 600 446 H 1200 M 600 464 H 1200 M 600 482 H 1200" stroke="#bde3c7" strokeOpacity=".12" strokeWidth="2" />
      <path d="M 618 392 H 785 V 443 H 618 Z M 677 349 H 763 V 443 H 677 Z M 716 322 H 769 V 443 H 716 Z" fill="#0b332e" opacity=".52" />
      <path d="M 624 429 H 758 M 644 412 H 726 M 680 380 H 746" stroke="#81b49a" strokeOpacity=".22" strokeWidth="3" />
      <path d="M 765 456 H 1056 V 520 H 765 Z" fill="#123d35" stroke="#82b597" strokeOpacity=".3" strokeWidth="2" />
      <path d="M 765 478 H 1056 M 765 500 H 1056 M 815 456 V 520 M 917 456 V 520 M 1017 456 V 520" fill="none" stroke="#a1c5a7" strokeOpacity=".15" strokeWidth="2" />

      <g filter="url(#construction-shadow)">
        <path d="M 1113 75 H 1143 V 446 H 1113 Z" fill="#245448" stroke="#e2cb8e" strokeWidth="3" />
        <path d="M 1116 118 L 1140 153 M 1140 118 L 1116 153 M 1116 157 L 1140 192 M 1140 157 L 1116 192 M 1116 196 L 1140 231 M 1140 196 L 1116 231 M 1116 235 L 1140 270 M 1140 235 L 1116 270 M 1116 274 L 1140 309 M 1140 274 L 1116 309 M 1116 313 L 1140 348 M 1140 313 L 1116 348 M 1116 352 L 1140 387 M 1140 352 L 1116 387 M 1116 391 L 1140 426 M 1140 391 L 1116 426" fill="none" stroke="#d4ba7b" strokeOpacity=".68" strokeWidth="2" />
        <path d="M 704 76 H 1191 V 104 H 704 Z" fill="#1c4c40" stroke="#ebd49a" strokeWidth="3" />
        <path d="M 704 76 L 740 104 M 740 76 L 776 104 M 776 76 L 812 104 M 812 76 L 848 104 M 848 76 L 884 104 M 884 76 L 920 104 M 920 76 L 956 104 M 956 76 L 992 104 M 992 76 L 1028 104 M 1028 76 L 1064 104 M 1064 76 L 1100 104 M 1100 76 L 1136 104 M 1136 76 L 1172 104" fill="none" stroke="#e3ca89" strokeOpacity=".78" strokeWidth="2" />
        {CRANE_BRACES.map(index => <path key={index} d={`M ${721 + index * 36} 76 V 104`} stroke="#e3ca89" strokeOpacity=".42" strokeWidth="2" />)}
        <path d="M 1128 43 V 76 M 1128 43 L 1099 76 M 1128 43 L 1157 76" fill="none" stroke="#f0d79c" strokeWidth="3" />
        <path d="M 1164 105 H 1199 V 130 H 1171 Z" fill="#86683d" stroke="#f0d79c" strokeWidth="2" />
        <path d="M 1091 105 H 1142 L 1134 131 H 1099 Z" fill="#b6844f" stroke="#f0d79c" strokeWidth="2" />
        <path d="M 1101 108 H 1130 L 1126 123 H 1105 Z" fill="#164b4b" stroke="#cde5bb" strokeOpacity=".7" strokeWidth="2" />
      </g>
      <circle className="catalog-construction-warning" cx="1128" cy="43" r="28" fill="url(#construction-lamp)" />
      <circle cx="1128" cy="43" r="4" fill="#ffe5a7" />

      <g filter="url(#construction-shadow)">
        <path d="M 765 440 H 1056 V 456 H 765 Z" fill="#8e815c" stroke="#e6d19a" strokeOpacity=".65" strokeWidth="2" />
        <path d="M 793 131 H 1017 V 440 H 793 Z" fill="#103f3b" stroke="#78cba4" strokeOpacity=".55" strokeWidth="3" />
        <path d="M 816 210 H 865 V 306 H 816 Z M 917 258 H 968 V 306 H 917 Z" fill="url(#construction-glass)" opacity=".5" />
        <path d="M 824 218 H 857 M 925 266 H 960" stroke="#cbf9d8" strokeOpacity=".64" strokeWidth="3" />
        <path d="M 815 131 V 440 M 866 131 V 440 M 917 131 V 440 M 968 131 V 440 M 1017 131 V 440" stroke="url(#construction-steel)" strokeWidth="6" />
        <path d="M 793 160 H 1017 M 793 209 H 1017 M 793 258 H 1017 M 793 307 H 1017 M 793 356 H 1017 M 793 405 H 1017" stroke="url(#construction-steel)" strokeWidth="5" />
        <path d="M 815 160 L 866 209 M 866 160 L 815 209 M 917 160 L 968 209 M 968 160 L 917 209 M 866 209 L 917 258 M 917 209 L 866 258 M 968 209 L 1017 258 M 1017 209 L 968 258" fill="none" stroke="#c6b780" strokeOpacity=".55" strokeWidth="3" />
        <path d="M 793 131 H 866 M 917 131 H 968 M 968 131 H 1017 M 842 111 V 131 M 944 106 V 131" stroke="#f0d898" strokeWidth="5" strokeLinecap="round" />
        <path d="M 797 312 H 1013 V 438 H 797 Z" fill="url(#construction-glass)" stroke="#85e6b8" strokeOpacity=".78" strokeWidth="3" />
        <path d="M 815 312 V 438 M 866 312 V 438 M 917 312 V 438 M 968 312 V 438 M 797 356 H 1013 M 797 405 H 1013" fill="none" stroke="#c9f2cf" strokeOpacity=".48" strokeWidth="3" />
        {BUILDING_WINDOWS.map(row => <path key={row} className={row % 2 === 0 ? 'catalog-construction-window' : undefined} style={row % 2 === 0 ? { animationDelay: `${row * -.8}s` } : undefined} d={`M ${827 + row * 34} 326 H ${839 + row * 34} M ${827 + row * 34} 374 H ${839 + row * 34} M ${827 + row * 34} 420 H ${839 + row * 34}`} stroke="#d8fff0" strokeOpacity=".74" strokeWidth="5" strokeLinecap="round" />)}
        <path d="M 766 188 H 793 M 766 237 H 793 M 766 286 H 793 M 766 335 H 793 M 766 384 H 793 M 766 433 H 793 M 774 147 V 442" stroke="#d2c28b" strokeOpacity=".65" strokeWidth="3" />
        <path d="M 1020 170 H 1056 V 439 H 1020 M 1020 218 H 1056 M 1020 266 H 1056 M 1020 314 H 1056 M 1020 362 H 1056 M 1020 410 H 1056" fill="none" stroke="#aaae7e" strokeOpacity=".42" strokeWidth="3" />
        <path d="M 1018 131 L 1041 147 V 439 H 1018 Z" fill="#0b4c45" fillOpacity=".55" stroke="#8edbb0" strokeOpacity=".43" strokeWidth="2" />
        <path d="M 1021 184 H 1038 M 1021 232 H 1038 M 1021 280 H 1038 M 1021 328 H 1038 M 1021 376 H 1038" stroke="#bcf3cf" strokeOpacity=".43" strokeWidth="2" />
      </g>

      <g transform="translate(1057 104)"><path className="catalog-construction-cable" d="M 0 0 V 116" fill="none" stroke="#f4dfa8" strokeWidth="2.5" /></g>
      <g transform="translate(1057 220)">
        <g className="catalog-construction-load">
          <path d="M 0 0 V 12 M -7 11 C -7 20 7 20 7 11" fill="none" stroke="#f4dfa8" strokeWidth="3" strokeLinecap="round" />
          <path d="M -32 23 L 0 11 L 32 23" fill="none" stroke="#f4dfa8" strokeWidth="2" />
          <rect x="-36" y="23" width="72" height="75" rx="3" fill="url(#construction-glass)" stroke="#f1d89b" strokeWidth="4" />
          <path d="M -30 34 H 30 M -30 63 H 30 M -4 26 V 95" stroke="#c1f3d3" strokeOpacity=".58" strokeWidth="2" />
          <path d="M -33 25 L 27 25 L -22 96 H -34 Z" fill="#e2ffda" opacity=".12" />
        </g>
      </g>

      <g className="catalog-construction-price" filter="url(#construction-shadow)">
        <rect x="1050" y="54" width="136" height="54" rx="22" fill="#0b3933" stroke="#83e1ad" strokeWidth="2.5" />
        <text x="1118" y="91" textAnchor="middle" fill="#c0f6d3" fontFamily="Inter, Arial, sans-serif" fontSize="30" fontWeight="800">$???</text>
      </g>
      <path d="M 697 469 H 1066 M 718 486 H 1036" stroke="#c7e0bd" strokeOpacity=".16" strokeWidth="2" />
      </g>
    </svg>
  </div>
}
