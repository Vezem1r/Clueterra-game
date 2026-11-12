import type { GameFamily } from '@/features/games/gameCatalog'

import './gameCatalogMotion.css'

interface GameCatalogMotionProps {
  family: GameFamily
}

const STAR = 'M 0 -12 C 1.5 -2.5 2.5 -1.5 12 0 C 2.5 1.5 1.5 2.5 0 12 C -1.5 2.5 -2.5 1.5 -12 0 C -2.5 -1.5 -1.5 -2.5 0 -12 Z'

function Twinkle({ x, y, size, delay, color }: { x: number, y: number, size: number, delay: number, color: string }) {
  return <g transform={`translate(${x} ${y}) scale(${size})`}>
    <path className="catalog-art-star" d={STAR} fill={color} style={{ animationDelay: `${delay}s` }} />
  </g>
}

function WavingFlag({ x, y, width, height, tilt, kind, delay }: {
  x: number
  y: number
  width: number
  height: number
  tilt: number
  kind: 'france' | 'japan' | 'sweden'
  delay: number
}) {
  const edge = width + 4
  const shape = `M 4 0 C ${width * .34} -5 ${width * .7} 5 ${edge} 0 C ${edge + 5} ${height * .32} ${edge - 4} ${height * .68} ${edge} ${height} C ${width * .7} ${height - 4} ${width * .34} ${height + 4} 4 ${height} Z`
  const clipId = `catalog-flag-${kind}`
  const cloth = kind === 'france'
    ? <><rect x="4" width={width / 3} height={height} fill="#1647a2" /><rect x={4 + width / 3} width={width / 3} height={height} fill="#fffdf2" /><rect x={4 + width * 2 / 3} width={width / 3} height={height} fill="#e7424f" /></>
    : kind === 'japan'
      ? <><rect x="4" width={width} height={height} fill="#fffdf2" /><circle cx={4 + width / 2} cy={height / 2} r={height * .29} fill="#bc1736" /></>
      : <><rect x="4" width={width} height={height} fill="#1766a5" /><path d={`M ${4 + width * .37} 0 V ${height} M 4 ${height * .5} H ${edge}`} stroke="#f6d54d" strokeWidth={height * .13} /></>

  return <g transform={`translate(${x} ${y}) rotate(${tilt})`}>
    <g transform="skewY(-5)">
      <g className="catalog-art-flag-cloth" style={{ animationDelay: `${delay}s` }}>
        <defs><clipPath id={clipId}><path d={shape} /></clipPath></defs>
        <g clipPath={`url(#${clipId})`}>
          {cloth}
          <rect className="catalog-art-flag-fold" x="-42" y="-10" width="70" height={height + 20} fill="url(#catalog-flag-fold)" style={{ animationDelay: `${delay}s` }} />
        </g>
        <path d={shape} fill="none" stroke="#f5dfb6" strokeOpacity=".88" strokeWidth="2.5" />
      </g>
    </g>
  </g>
}

export function GameCatalogMotion({ family }: GameCatalogMotionProps) {
  if (family === 'clues') return <svg className="catalog-art-motion" viewBox="0 0 1200 520" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
    <Twinkle x={738} y={118} size={1.8} delay={-1} color="#f4d8ff" />
    <Twinkle x={1059} y={264} size={1.45} delay={-2.5} color="#f9eaff" />
    <Twinkle x={1123} y={107} size={1.1} delay={-3.9} color="#d0f8ef" />
    <Twinkle x={687} y={329} size={1} delay={-5} color="#cbb9ff" />
  </svg>

  if (family === 'silhouette') return <svg className="catalog-art-motion" viewBox="0 0 720 540" preserveAspectRatio="xMidYMin slice" aria-hidden="true">
    <defs>
      <clipPath id="catalog-shape-clip"><rect x="366" y="33" width="304" height="350" rx="26" /></clipPath>
      <linearGradient id="catalog-shape-scan"><stop stopColor="#bdfcff" stopOpacity="0" /><stop offset=".5" stopColor="#e3ffff" stopOpacity=".24" /><stop offset="1" stopColor="#bdfcff" stopOpacity="0" /></linearGradient>
    </defs>
    <g transform="rotate(-5 518 205)" clipPath="url(#catalog-shape-clip)">
      <rect className="catalog-art-shape-scan" x="340" y="18" width="100" height="390" fill="url(#catalog-shape-scan)" />
    </g>
  </svg>

  if (family === 'flag') return <svg className="catalog-art-motion" viewBox="0 0 720 540" preserveAspectRatio="xMidYMin slice" aria-hidden="true">
    <defs>
      <linearGradient id="catalog-flag-fold"><stop stopColor="#071d36" stopOpacity="0" /><stop offset=".5" stopColor="#071d36" stopOpacity=".18" /><stop offset=".78" stopColor="#fff" stopOpacity=".18" /><stop offset="1" stopColor="#fff" stopOpacity="0" /></linearGradient>
    </defs>
    <WavingFlag x={195} y={78} width={130} height={90} tilt={-5} kind="france" delay={-10} />
    <WavingFlag x={345} y={55} width={140} height={96} tilt={3} kind="japan" delay={0} />
    <WavingFlag x={500} y={104} width={114} height={80} tilt={7} kind="sweden" delay={-5} />
  </svg>

  if (family === 'quiz') return <svg className="catalog-art-motion" viewBox="0 0 720 540" preserveAspectRatio="xMidYMin slice" aria-hidden="true">
    <Twinkle x={335} y={107} size={1} delay={-0.4} color="#fff4c7" />
    <Twinkle x={550} y={65} size={1.2} delay={-2.2} color="#fff0c0" />
    <Twinkle x={689} y={245} size={0.85} delay={-4.1} color="#c7f5eb" />
  </svg>

  return null
}
