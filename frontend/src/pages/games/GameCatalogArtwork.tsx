import './gameCatalogArtwork.css'

const TOWER_WINDOWS = [
  [973, 202], [998, 202], [1023, 202], [973, 235], [998, 235], [1023, 235],
  [973, 268], [998, 268], [1023, 268], [973, 301], [998, 301], [1023, 301],
  [973, 334], [998, 334], [1023, 334], [973, 367], [998, 367], [1023, 367],
]
const COST_LINE = 'M 719 269 C 777 254 817 203 858 172 C 908 136 958 138 997 111 C 1059 84 1108 138 1135 183'

export function BudgetCatalogArtwork() {
  return <div className="catalog-budget-art" aria-hidden="true">
    <svg className="catalog-budget-scene" viewBox="0 0 1200 520" preserveAspectRatio="xMaxYMid slice">
      <defs>
        <linearGradient id="budget-back" x1="0" y1="0" x2="1" y2="1">
          <stop stopColor="#12231f" /><stop offset=".58" stopColor="#18332f" /><stop offset="1" stopColor="#111d25" />
        </linearGradient>
        <linearGradient id="budget-gold" x1="0" y1="0" x2="1" y2="1">
          <stop stopColor="#f3d598" /><stop offset="1" stopColor="#bc9255" />
        </linearGradient>
        <linearGradient id="budget-green-building" x1="0" y1="0" x2="1" y2="1">
          <stop stopColor="#35bb91" /><stop offset=".55" stopColor="#197b67" /><stop offset="1" stopColor="#0e4a47" />
        </linearGradient>
        <linearGradient id="budget-amber-building" x1="0" y1="0" x2="1" y2="1">
          <stop stopColor="#d79a4f" /><stop offset=".52" stopColor="#a86739" /><stop offset="1" stopColor="#513d32" />
        </linearGradient>
        <radialGradient id="budget-halo">
          <stop stopColor="#b8924e" stopOpacity=".26" /><stop offset="1" stopColor="#b8924e" stopOpacity="0" />
        </radialGradient>
        <linearGradient id="budget-chip-glint"><stop stopColor="#fff" stopOpacity="0" /><stop offset=".5" stopColor="#fff" stopOpacity=".38" /><stop offset="1" stopColor="#fff" stopOpacity="0" /></linearGradient>
        <pattern id="budget-grid" width="44" height="44" patternUnits="userSpaceOnUse">
          <path d="M 44 0 H 0 V 44" fill="none" stroke="#d4e5c6" strokeOpacity=".075" />
        </pattern>
        <filter id="budget-chip-shadow" x="-50%" y="-100%" width="200%" height="300%"><feDropShadow dx="0" dy="12" stdDeviation="12" floodColor="#021710" floodOpacity=".55" /></filter>
        <filter id="budget-line-glow" x="-30%" y="-100%" width="160%" height="300%"><feGaussianBlur stdDeviation="6" /></filter>
        <clipPath id="budget-chip-clip"><rect x="1084" y="96" width="94" height="50" rx="23" /></clipPath>
      </defs>
      <rect width="1200" height="520" fill="url(#budget-back)" />
      <rect width="1200" height="520" fill="url(#budget-grid)" />
      <circle cx="948" cy="224" r="330" fill="url(#budget-halo)" />
      <circle cx="1025" cy="305" r="240" fill="#35c18b" opacity=".08" />
      <circle cx="1147" cy="335" r="150" fill="#e8aa60" opacity=".08" />
      <ellipse cx="970" cy="432" rx="280" ry="55" fill="#49c897" opacity=".11" />
      <path d="M 570 430 H 1220 M 570 442 H 1220" stroke="#d8c895" strokeOpacity=".25" strokeWidth="2" />
      <path d="M 603 397 H 1198 M 603 341 H 1198 M 603 285 H 1198 M 603 229 H 1198" stroke="#c9e3c2" strokeOpacity=".12" strokeWidth="2" strokeDasharray="4 12" />
      <path d="M 650 58 V 432 M 770 58 V 432 M 890 58 V 432 M 1010 58 V 432 M 1130 58 V 432" stroke="#c9e3c2" strokeOpacity=".07" strokeWidth="2" />

      <path d="M 667 436 V 294 H 691 V 278 H 746 V 293 H 772 V 436 Z" fill="url(#budget-amber-building)" stroke="#ffd896" strokeOpacity=".8" strokeWidth="3" />
      <path d="M 772 294 L 786 304 V 436 H 772 Z" fill="#674937" stroke="#ffd896" strokeOpacity=".33" strokeWidth="2" />
      <path d="M 678 435 V 304 H 762 V 435" fill="none" stroke="#f2d69a" strokeOpacity=".19" strokeWidth="2" />
      <path d="M 696 325 H 743 M 696 351 H 743 M 696 377 H 743 M 696 403 H 743" stroke="#ffe4b3" strokeOpacity=".65" strokeWidth="5" strokeLinecap="round" strokeDasharray="10 7" />

      <path d="M 795 436 V 246 C 795 203 824 176 858 176 C 892 176 921 203 921 246 V 436 Z" fill="url(#budget-green-building)" stroke="#81e5ba" strokeOpacity=".9" strokeWidth="3" />
      <path d="M 921 246 L 935 257 V 436 H 921 Z" fill="#0a534d" stroke="#8ce8c0" strokeOpacity=".35" strokeWidth="2" />
      <path d="M 808 436 V 248 C 808 216 828 192 858 192 C 888 192 908 216 908 248 V 436" fill="none" stroke="#e2d6a9" strokeOpacity=".23" strokeWidth="2" />
      <path d="M 858 188 V 436 M 807 263 H 909 M 807 313 H 909 M 807 363 H 909" stroke="#e8cf94" strokeOpacity=".24" strokeWidth="2" />
      <path d="M 829 271 H 845 M 870 271 H 886 M 829 324 H 845 M 870 324 H 886 M 829 377 H 845 M 870 377 H 886" stroke="#d7fff0" strokeOpacity=".72" strokeWidth="8" strokeLinecap="round" />

      <path d="M 954 436 V 150 H 967 V 133 H 985 V 111 H 1010 V 133 H 1028 V 150 H 1041 V 436 Z" fill="url(#budget-green-building)" stroke="#9af1c8" strokeOpacity=".94" strokeWidth="3" />
      <path d="M 1041 150 L 1055 161 V 436 H 1041 Z" fill="#0c5148" stroke="#9af1c8" strokeOpacity=".38" strokeWidth="2" />
      <path d="M 997 110 V 59 M 988 63 H 1006" stroke="#f1d49d" strokeWidth="4" strokeLinecap="round" />
      <path d="M 963 166 H 1033 M 963 192 H 1033 M 963 396 H 1033" stroke="#ffe3aa" strokeOpacity=".26" strokeWidth="2" />
      {TOWER_WINDOWS.map(([x, y], index) => <rect key={index} className={index % 5 === 0 ? 'catalog-budget-window' : undefined} style={index % 5 === 0 ? { animationDelay: `${index * -.37}s` } : undefined} x={x} y={y} width="11" height="12" rx="1.5" fill="#d7fff0" opacity={index % 3 === 0 ? '.84' : '.55'} />)}

      <path d="M 1071 436 V 264 L 1095 245 L 1175 245 L 1198 264 V 436 Z" fill="url(#budget-amber-building)" stroke="#ffd395" strokeOpacity=".83" strokeWidth="3" />
      <path d="M 1198 264 L 1212 274 V 436 H 1198 Z" fill="#674533" stroke="#ffd395" strokeOpacity=".37" strokeWidth="2" />
      <path d="M 1067 263 C 1084 203 1110 188 1135 188 C 1160 188 1184 209 1201 263" fill="#b87842" stroke="#f2ce8f" strokeOpacity=".9" strokeWidth="3" />
      <path d="M 1085 273 H 1188 M 1092 309 H 1182 M 1092 346 H 1182 M 1092 383 H 1182" stroke="#e9d49e" strokeOpacity=".28" strokeWidth="3" />
      <path d="M 1100 273 V 432 M 1135 267 V 432 M 1170 273 V 432" stroke="#e9d49e" strokeOpacity=".24" strokeWidth="2" />

      <path d={COST_LINE} fill="none" stroke="#edd49b" strokeOpacity=".18" strokeWidth="15" />
      <path d={COST_LINE} fill="none" stroke="url(#budget-gold)" strokeWidth="3" strokeDasharray="8 11" strokeLinecap="round" />
      <path className="catalog-budget-trace" d={COST_LINE} pathLength="100" fill="none" stroke="#ffeabd" strokeWidth="19" strokeDasharray="10 100" strokeLinecap="round" filter="url(#budget-line-glow)" />
      <path className="catalog-budget-trace" d={COST_LINE} pathLength="100" fill="none" stroke="#fff4d6" strokeWidth="4" strokeDasharray="10 100" strokeLinecap="round" />
      <circle cx="719" cy="269" r="6" fill="#e8d79f" />
      <circle cx="858" cy="172" r="6" fill="#e8d79f" />
      <circle cx="1135" cy="183" r="6" fill="#e8d79f" />
      <circle className="catalog-budget-pulse" cx="997" cy="111" r="13" fill="#eed198" opacity=".2" />
      <circle cx="997" cy="111" r="6" fill="#fff1c1" />
      <g filter="url(#budget-chip-shadow)">
        <rect x="1084" y="96" width="94" height="50" rx="23" fill="#123b37" stroke="#77e3b4" strokeOpacity=".85" strokeWidth="2" />
        <text x="1131" y="130" textAnchor="middle" fill="#b4f8d2" fontFamily="Inter, Arial, sans-serif" fontSize="28" fontWeight="800">$?</text>
        <g clipPath="url(#budget-chip-clip)"><path className="catalog-budget-glint" d="M 1044 89 H 1074 L 1056 154 H 1026 Z" fill="url(#budget-chip-glint)" /></g>
      </g>
      <path d="M 600 460 H 1200 M 623 479 H 1184 M 680 498 H 1128" stroke="#94c7ae" strokeOpacity=".12" strokeWidth="2" />
    </svg>
  </div>
}
