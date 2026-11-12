import { apiClient } from './client'
import type {
  Friend,
  FriendLeaderboardEntry,
  FriendRequests,
  FriendUser,
  RelationshipLeaderboardPeriod,
} from '@/types'

export const friendsApi = {
  searchUsers: async (query: string): Promise<FriendUser[]> => {
    const { data } = await apiClient.get<FriendUser[]>('/friends/users/search', { params: { q: query } })
    return data
  },

  getFriends: async (): Promise<Friend[]> => {
    const { data } = await apiClient.get<Friend[]>('/friends')
    return data
  },

  getFriendRequests: async (): Promise<FriendRequests> => {
    const { data } = await apiClient.get<FriendRequests>('/friends/requests')
    return data
  },

  requestFriend: async (targetUserId: string): Promise<void> => {
    await apiClient.post(`/friends/requests/${targetUserId}`)
  },

  acceptFriendRequest: async (requestId: string): Promise<void> => {
    await apiClient.post(`/friends/requests/${requestId}/accept`)
  },

  deleteFriendRequest: async (requestId: string): Promise<void> => {
    await apiClient.delete(`/friends/requests/${requestId}`)
  },

  removeFriend: async (friendId: string): Promise<void> => {
    await apiClient.delete(`/friends/${friendId}`)
  },

  blockUser: async (blockedUserId: string): Promise<void> => {
    await apiClient.post(`/friends/blocks/${blockedUserId}`)
  },

  unblockUser: async (blockedUserId: string): Promise<void> => {
    await apiClient.delete(`/friends/blocks/${blockedUserId}`)
  },

  getFriendsLeaderboard: async (period: RelationshipLeaderboardPeriod): Promise<FriendLeaderboardEntry[]> => {
    const { data } = await apiClient.get<FriendLeaderboardEntry[]>('/friends/leaderboard', {
      params: { period },
    })
    return data
  },
}
