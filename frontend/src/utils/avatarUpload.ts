const ALLOWED_AVATAR_TYPES = new Set(['image/jpeg', 'image/png'])

export const AVATAR_FILE_ACCEPT = 'image/jpeg,image/png'

export function validateAvatarFile(file: File): string | null {
  if (!ALLOWED_AVATAR_TYPES.has(file.type)) {
    return 'Use a JPEG or PNG image for avatars.'
  }
  return null
}
