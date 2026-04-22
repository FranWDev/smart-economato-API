import { GetOrdersBulkTool } from '../get-orders-bulk.tool';
import { InventoryClient } from '../../../mcp/inventory.client';
import { MCP_BASE } from '../../constants';

describe('GetOrdersBulkTool', () => {
  let tool: GetOrdersBulkTool;
  let mockClient: Partial<InventoryClient>;

  beforeEach(() => {
    tool = new GetOrdersBulkTool();
    mockClient = {
      post: jest.fn().mockResolvedValue([{ id: 10 }, { id: 11 }]),
    };
  });

  describe('schema validation', () => {
    it('accepts ids array', () => {
      const result = tool.schema.parse({ ids: [10, 11] });
      expect(result).toEqual({ ids: [10, 11] });
    });

    it('rejects invalid ids type', () => {
      expect(() => tool.schema.parse({ ids: ['x'] })).toThrow();
    });
  });

  describe('execute', () => {
    it('calls client.post with correct endpoint and payload', async () => {
      await tool.execute(
        { ids: [10, 11] },
        mockClient as InventoryClient,
        'jwt',
      );

      expect(mockClient.post).toHaveBeenCalledWith(
        `${MCP_BASE}/bulk/orders`,
        [10, 11],
        'jwt',
      );
    });
  });
});
