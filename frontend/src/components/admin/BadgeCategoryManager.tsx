import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { DragDropContext, Droppable, Draggable, type DropResult } from '@hello-pangea/dnd'
import { GripVertical, Plus, Edit2, Trash2, Check, X } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { adminApi } from '@/api/admin'
import { useToastStore } from '@/store/toastStore'
import { extractApiErrorMessage } from '@/utils/errors'
import type { BadgeCategoryDto } from '@/types'

interface Props {
  onCategoryChange?: () => void
}

export function BadgeCategoryManager({ onCategoryChange }: Props) {
  const queryClient = useQueryClient()
  const toast = useToastStore()
  const [newCategoryName, setNewCategoryName] = useState('')
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editingName, setEditingName] = useState('')
  const [localCategories, setLocalCategories] = useState<BadgeCategoryDto[] | null>(null)

  const { data: categories } = useQuery({
    queryKey: ['admin-badge-categories'],
    queryFn: adminApi.getCategories,
    staleTime: 30_000,
  })

  const displayCategories = localCategories ?? categories ?? []

  const invalidate = () => {
    setLocalCategories(null)
    queryClient.invalidateQueries({ queryKey: ['admin-badge-categories'] })
    onCategoryChange?.()
  }

  const createMutation = useMutation({
    mutationFn: adminApi.createCategory,
    onSuccess: () => { invalidate(); setNewCategoryName('') },
    onError: (err) => toast.addToast({ type: 'error', message: extractApiErrorMessage(err, 'Failed to create category') }),
  })

  const renameMutation = useMutation({
    mutationFn: ({ id, name }: { id: string; name: string }) => adminApi.renameCategory(id, name),
    onSuccess: () => { invalidate(); setEditingId(null) },
    onError: (err) => toast.addToast({ type: 'error', message: extractApiErrorMessage(err, 'Failed to rename') }),
  })

  const deleteMutation = useMutation({
    mutationFn: adminApi.deleteCategory,
    onSuccess: invalidate,
    onError: (err) => toast.addToast({ type: 'error', message: extractApiErrorMessage(err, 'Failed to delete category') }),
  })

  const reorderMutation = useMutation({
    mutationFn: adminApi.reorderCategories,
    onError: (err) => { invalidate(); toast.addToast({ type: 'error', message: extractApiErrorMessage(err, 'Failed to save order') }) },
  })

  const handleDragEnd = (result: DropResult) => {
    if (!result.destination || result.destination.index === result.source.index) return
    const reordered = [...displayCategories]
    const [moved] = reordered.splice(result.source.index, 1)
    reordered.splice(result.destination.index, 0, moved)
    setLocalCategories(reordered)
    reorderMutation.mutate(reordered.map(c => c.id))
  }

  const handleCreate = () => {
    const name = newCategoryName.trim()
    if (!name) return
    createMutation.mutate(name)
  }

  const startEdit = (cat: BadgeCategoryDto) => {
    setEditingId(cat.id)
    setEditingName(cat.name)
  }

  const confirmEdit = () => {
    if (!editingId || !editingName.trim()) return
    renameMutation.mutate({ id: editingId, name: editingName.trim() })
  }

  return (
    <div className="rounded-xl border border-white/[.06] bg-white/[.02] p-4 space-y-3">
      <p className="text-[10px] text-white/25 uppercase tracking-widest">Categories</p>

      <DragDropContext onDragEnd={handleDragEnd}>
        <Droppable droppableId="categories">
          {(provided) => (
            <div ref={provided.innerRef} {...provided.droppableProps} className="space-y-1">
              {displayCategories.map((cat, index) => (
                <Draggable key={cat.id} draggableId={cat.id} index={index}>
                  {(drag) => (
                    <div
                      ref={drag.innerRef}
                      {...drag.draggableProps}
                      className="flex items-center gap-2 rounded-lg bg-white/[.04] border border-white/[.06] px-2 py-1.5"
                    >
                      <div {...drag.dragHandleProps} className="text-white/20 hover:text-white/50 cursor-grab active:cursor-grabbing">
                        <GripVertical size={14} />
                      </div>

                      {editingId === cat.id ? (
                        <>
                          <Input
                            value={editingName}
                            onChange={e => setEditingName(e.target.value)}
                            onKeyDown={e => { if (e.key === 'Enter') confirmEdit(); if (e.key === 'Escape') setEditingId(null) }}
                            className="flex-1 h-7 text-sm"
                            autoFocus
                          />
                          <button onClick={confirmEdit} className="text-emerald-400 hover:text-emerald-300 cursor-pointer p-0.5">
                            <Check size={13} />
                          </button>
                          <button onClick={() => setEditingId(null)} className="text-white/30 hover:text-white/60 cursor-pointer p-0.5">
                            <X size={13} />
                          </button>
                        </>
                      ) : (
                        <>
                          <span className="flex-1 text-sm text-white/80">{cat.name}</span>
                          <span className="text-xs text-white/25 font-mono">{cat.badgeCount}</span>
                          <button onClick={() => startEdit(cat)} className="text-white/25 hover:text-white/60 cursor-pointer p-0.5">
                            <Edit2 size={12} />
                          </button>
                          <button
                            onClick={() => deleteMutation.mutate(cat.id)}
                            disabled={cat.badgeCount > 0}
                            className="text-white/25 hover:text-red-400 cursor-pointer p-0.5 disabled:opacity-30 disabled:cursor-not-allowed"
                          >
                            <Trash2 size={12} />
                          </button>
                        </>
                      )}
                    </div>
                  )}
                </Draggable>
              ))}
              {provided.placeholder}
            </div>
          )}
        </Droppable>
      </DragDropContext>

      <div className="flex items-center gap-2">
        <Input
          placeholder="New category name..."
          value={newCategoryName}
          onChange={e => setNewCategoryName(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && handleCreate()}
          className="flex-1 h-8 text-sm"
        />
        <Button
          variant="outline"
          size="sm"
          onClick={handleCreate}
          isLoading={createMutation.isPending}
          disabled={!newCategoryName.trim()}
        >
          <Plus size={13} />
        </Button>
      </div>
    </div>
  )
}
