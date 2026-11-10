import { Button } from '@/components/ui/button'
import { Table, TableRow } from '@/components/ui/table'
import StatusPill from '@/components/StatusPill'

export function PageA() {
  return (
    <div>
      <Button>a</Button>
      <Button>b</Button>
      <StatusPill />
      <Table>
        <TableRow />
      </Table>
      <input type="text" />
    </div>
  )
}
