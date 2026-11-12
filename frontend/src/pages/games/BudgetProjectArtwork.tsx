import './catalogNewArtwork.css'

const ROOF_MARKS = Array.from({ length: 17 }, (_, index) => index)

export function BudgetProjectArtwork() {
  return <div className="catalog-project-art" aria-hidden="true">
    <svg className="catalog-project-scene" viewBox="0 0 1200 520" preserveAspectRatio="xMaxYMid slice">
      <defs>
        <linearGradient id="project-back" x1="0" y1="0" x2="1" y2="1">
          <stop stopColor="#081b1c" /><stop offset=".62" stopColor="#113c37" /><stop offset="1" stopColor="#1d2b27" />
        </linearGradient>
        <radialGradient id="project-glow"><stop stopColor="#d8b669" stopOpacity=".24" /><stop offset="1" stopColor="#d8b669" stopOpacity="0" /></radialGradient>
        <linearGradient id="project-roof" x1="0" y1="0" x2=".7" y2="1">
          <stop stopColor="#a8d3b4" /><stop offset=".5" stopColor="#518d78" /><stop offset="1" stopColor="#214e46" />
        </linearGradient>
        <linearGradient id="project-facade" x1="0" y1="0" x2="0" y2="1">
          <stop stopColor="#3d7d6b" /><stop offset="1" stopColor="#102e2f" />
        </linearGradient>
        <pattern id="project-grid" width="42" height="42" patternUnits="userSpaceOnUse">
          <path d="M 42 0 H 0 V 42" fill="none" stroke="#c8ecdd" strokeOpacity=".075" />
          <circle cx="0" cy="0" r="1.5" fill="#c8ecdd" opacity=".18" />
        </pattern>
        <filter id="project-shadow" x="-30%" y="-50%" width="160%" height="200%"><feDropShadow dx="0" dy="19" stdDeviation="18" floodColor="#020d12" floodOpacity=".58" /></filter>
        <filter id="project-line-glow" x="-20%" y="-100%" width="140%" height="300%"><feGaussianBlur stdDeviation="5" /></filter>
      </defs>

      <rect width="1200" height="520" fill="url(#project-back)" />
      <rect width="1200" height="520" fill="url(#project-grid)" />
      <circle cx="952" cy="282" r="320" fill="url(#project-glow)" />
      <path d="M 600 455 H 1200 M 600 470 H 1200 M 600 485 H 1200" stroke="#c8ecdd" strokeOpacity=".12" strokeWidth="2" />
      <path d="M 687 80 V 450 M 786 80 V 450 M 885 80 V 450 M 984 80 V 450 M 1083 80 V 450" stroke="#c8ecdd" strokeOpacity=".07" strokeWidth="2" />

      <g filter="url(#project-shadow)">
        <path d="M 692 299 C 692 216 807 154 942 154 C 1077 154 1192 216 1192 299 V 356 C 1192 438 1077 493 942 493 C 807 493 692 438 692 356 Z" fill="url(#project-facade)" stroke="#8cc6a5" strokeOpacity=".55" strokeWidth="3" />
        <path d="M 707 329 C 761 399 847 424 942 424 C 1040 424 1124 398 1177 329" fill="none" stroke="#b5dbb2" strokeOpacity=".3" strokeWidth="2" />
        {ROOF_MARKS.map(index => {
          const x = 720 + index * 28
          return <path key={index} d={`M ${x} 378 V 426`} stroke="#b4d7b7" strokeOpacity={index % 3 === 0 ? '.47' : '.24'} strokeWidth="3" />
        })}
        <ellipse cx="942" cy="296" rx="250" ry="144" fill="url(#project-roof)" stroke="#e2d798" strokeOpacity=".85" strokeWidth="4" />
        <ellipse cx="942" cy="296" rx="231" ry="129" fill="none" stroke="#d9e3b2" strokeOpacity=".42" strokeWidth="2" strokeDasharray="7 12" />
        <ellipse cx="942" cy="296" rx="207" ry="110" fill="#234b43" stroke="#b9d8ad" strokeOpacity=".85" strokeWidth="4" />
        <ellipse cx="942" cy="296" rx="184" ry="91" fill="#092e2e" stroke="#7cb59d" strokeOpacity=".72" strokeWidth="3" />
        <ellipse cx="942" cy="296" rx="167" ry="76" fill="#255d47" stroke="#a9d7a5" strokeOpacity=".75" strokeWidth="2" />
        <rect x="839" y="251" width="206" height="91" rx="4" fill="#2b6c4c" stroke="#d1e4b2" strokeOpacity=".7" strokeWidth="2" />
        <path d="M 942 252 V 341 M 840 296 H 1044 M 877 252 V 341 M 1007 252 V 341" fill="none" stroke="#e5e8c3" strokeOpacity=".5" strokeWidth="2" />
        <circle cx="942" cy="296" r="18" fill="none" stroke="#e5e8c3" strokeOpacity=".58" strokeWidth="2" />
        <path d="M 839 273 H 859 V 319 H 839 M 1045 273 H 1025 V 319 H 1045" fill="none" stroke="#e5e8c3" strokeOpacity=".55" strokeWidth="2" />
        <path className="catalog-project-roof-trace" d="M 692 296 C 692 214 806 152 942 152 C 1078 152 1192 214 1192 296" pathLength="100" fill="none" stroke="#fff1b7" strokeWidth="14" strokeDasharray="17 100" strokeLinecap="round" filter="url(#project-line-glow)" />
        <path className="catalog-project-roof-trace" d="M 692 296 C 692 214 806 152 942 152 C 1078 152 1192 214 1192 296" pathLength="100" fill="none" stroke="#fff4cf" strokeWidth="3" strokeDasharray="17 100" strokeLinecap="round" />
      </g>

      <path d="M 1032 154 V 118 H 1060 M 703 306 H 659 V 275 M 1073 421 H 1135 V 447" fill="none" stroke="#e8d59f" strokeOpacity=".72" strokeWidth="2" />
      <circle cx="703" cy="306" r="4" fill="#eedca7" /><circle cx="1073" cy="421" r="4" fill="#eedca7" />
      <path d="M 638 270 H 679 M 1139 448 H 1174" stroke="#e8d59f" strokeOpacity=".53" strokeWidth="2" strokeDasharray="4 4" />

      <g className="catalog-project-price-panel" filter="url(#project-shadow)">
        <rect x="1009" y="44" width="163" height="96" rx="16" fill="#0b2c2d" stroke="#e6cf88" strokeWidth="2" />
        <path d="M 1025 67 H 1091 M 1025 75 H 1065" stroke="#a9c4ae" strokeOpacity=".45" strokeWidth="3" strokeLinecap="round" />
        <path d="M 1024 85 H 1158" stroke="#e6cf88" strokeOpacity=".3" strokeWidth="2" />
        <text className="catalog-project-price" x="1090" y="122" textAnchor="middle" fill="#ffe4a0" fontFamily="Inter, Arial, sans-serif" fontSize="42" fontWeight="800" letterSpacing="2">$???</text>
      </g>
    </svg>
  </div>
}
